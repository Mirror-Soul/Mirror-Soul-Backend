package com.mirrorsoul.mirrorsoul_api.safety;

import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({SafetyReportService.class,SafetyDecisionService.class,SafetyConcurrencyTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SafetyConcurrencyTest {
    @Autowired UserRepository users;
    @Autowired SafetyReportRepository reportRepository;
    @Autowired SafetyDecisionRepository decisionRepository;
    @Autowired SafetyNoticeRepository notices;
    @Autowired SafetyReportService reports;
    @Autowired SafetyDecisionService decisions;
    @org.springframework.boot.test.context.TestConfiguration static class Config {
        @Bean SafetyPolicy safetyPolicy() { return new SafetyPolicy(10,50,90,7,14); }
    }
    private User user(UserRole role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash")
            .status(UserStatus.ACTIVE).role(role).createdAt(LocalDateTime.now()).build());
    }
    @Test void parallelSubmissionsAndDecisionsCannotDuplicateReceiptsOrSanctions() throws Exception {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN);
        var submit=new Submit(target.getUuid(),ReportType.HARASSMENT,"description",null,null,"same-key");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready=new CountDownLatch(2);
            java.util.function.Supplier<ReportView> receive=() -> {
                ready.countDown();await(ready);return reports.submit(reporter.getId(),submit);
            };
            var first=CompletableFuture.supplyAsync(receive,pool);var second=CompletableFuture.supplyAsync(receive,pool);
            var report=first.get(15,TimeUnit.SECONDS);
            assertThat(second.get(15,TimeUnit.SECONDS).id()).isEqualTo(report.id());
            assertThat(reportRepository.findByReporterId(reporter.getId(),org.springframework.data.domain.PageRequest.of(0,20)).getTotalElements()).isEqualTo(1);
            var assigned=reports.assign(admin.getId(),report.id(),new Assign(admin.getId(),report.version()));
            CountDownLatch decisionsReady=new CountDownLatch(2);
            java.util.function.Function<String,Object> decide=key -> {
                decisionsReady.countDown();await(decisionsReady);
                try { return decisions.decide(admin.getId(),report.id(),new Decide(Action.RESTRICT,Reason.VIOLATION_CONFIRMED,
                    "restriction",assigned.version(),target.getVersion(),7,key)); }
                catch(GeneralException e) { return e.getCode().getCode(); }
            };
            var a=CompletableFuture.supplyAsync(() -> decide.apply("decision-a"),pool);
            var b=CompletableFuture.supplyAsync(() -> decide.apply("decision-b"),pool);
            List<Object> results=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertThat(results.stream().filter(DecisionView.class::isInstance).count()).isEqualTo(1);
            assertThat(results).contains("SAFETY_4090");
            assertThat(decisionRepository.findByReportId(report.id(),org.springframework.data.domain.PageRequest.of(0,20)).getTotalElements()).isEqualTo(1);
            assertThat(notices.findByUserId(target.getId(),org.springframework.data.domain.PageRequest.of(0,20)).getTotalElements()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test timed out"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
    }
}
