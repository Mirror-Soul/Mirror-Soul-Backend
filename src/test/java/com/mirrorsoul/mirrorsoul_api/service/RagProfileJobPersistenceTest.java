package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;

import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.event.UserEmbeddingRefreshRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({RagProfileJobService.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RagProfileJobPersistenceTest {
    @Autowired RagProfileJobService service;
    @Autowired RagProfileJobRepository jobs;
    @Autowired CloneRepository clones;
    @Autowired UserRepository users;
    @Autowired MbtiProfileRepository mbtis;
    @Autowired InterviewRepository questions;
    @Autowired InterviewRecordRepository answers;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    private UUID uuid;
    private Long cloneId;
    private TransactionTemplate tx;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactions);
        uuid = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            var user = users.saveAndFlush(User.builder().uuid(uuid).email(uuid + "@example.com")
                    .passwordHash("hash").status(UserStatus.ONBOARD_D).birthDate(LocalDate.now().minusYears(24))
                    .gender(Gender.MALE).selfIntroduction("소개").build());
            cloneId = clones.saveAndFlush(Clone.builder().user(user).syncRate(0).build()).getId();
            mbtis.save(MbtiProfile.create(user, MbtiType.INFP, 50, 50, 50, 50));
            try {
                var constructor = Interview.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                var question = constructor.newInstance();
                ReflectionTestUtils.setField(question, "question", "가장 중요한 가치는?");
                questions.saveAndFlush(question);
                answers.save(InterviewRecord.create(user, question, "url", "key", "서로 존중하는 것"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void enqueue() {
        tx.executeWithoutResult(status -> events.publishEvent(new UserEmbeddingRefreshRequestedEvent(uuid, EmbeddingType.INTERVIEW)));
    }

    @Test
    void rollbackDoesNotLeaveDeliveryAndCommittedDataBuildsCorrectPayload() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            events.publishEvent(new UserEmbeddingRefreshRequestedEvent(uuid, EmbeddingType.INTERVIEW));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jobs.findById(cloneId)).isEmpty();
        enqueue();
        var claim = service.claim(cloneId);
        assertThat(claim.request().userId()).isEqualTo(uuid);
        assertThat(claim.request().cloneId()).isEqualTo(cloneId);
        assertThat(claim.request().aiProfileId()).isEqualTo("clone-" + cloneId);
        assertThat(claim.request().mbti()).isEqualTo("INFP");
        assertThat(claim.request().age()).isEqualTo(24);
        assertThat(claim.request().gender()).isEqualTo("male");
        assertThat(claim.request().interviewSamples().get(0).transcript()).isEqualTo("서로 존중하는 것");
        assertThat(service.claim(cloneId)).isNull();
        service.finish(claim, null);
        assertThat(service.claim(cloneId)).isNull();
        // Only the authenticated AI callback may set this flag.
        assertThat(clones.findById(cloneId).orElseThrow().isPersonalityTrainingCompleted()).isFalse();
    }

    @Test
    void failedDeliveryBacksOffAndNewRevisionIsNotLost() {
        enqueue();
        var first = service.claim(cloneId);
        service.finish(first, "ResourceAccessException");
        assertThat(service.claim(cloneId)).isNull();
        assertThat(jobs.findById(cloneId).orElseThrow().getAttempts()).isEqualTo(1);
        enqueue();
        var second = service.claim(cloneId);
        enqueue();
        service.finish(second, null);
        var third = service.claim(cloneId);
        assertThat(third.revision()).isGreaterThan(second.revision());
        assertThat(third.request().aiProfileId()).isEqualTo(first.request().aiProfileId());
    }

    @Test
    void expiredLeaseCanBeReclaimedAndStaleWorkerCannotCompleteIt() {
        enqueue();
        var first = service.claim(cloneId);
        tx.executeWithoutResult(status -> ReflectionTestUtils.setField(
                jobs.findLockedById(cloneId).orElseThrow(), "leaseUntil", LocalDateTime.now().minusSeconds(1)));
        var second = service.claim(cloneId);
        service.finish(first, null);
        assertThat(jobs.findById(cloneId).orElseThrow().getLeaseToken()).isEqualTo(second.token());
        service.finish(second, null);
        assertThat(service.claim(cloneId)).isNull();
    }

    @Test
    void concurrentWorkersOnlyClaimOnce() throws Exception {
        enqueue();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<RagProfileJobService.ClaimedProfile> task = () -> { start.await(); return service.claim(cloneId); };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            var one = first.get(10, TimeUnit.SECONDS);
            var two = second.get(10, TimeUnit.SECONDS);
            assertThat((one == null) != (two == null)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void initialProfileWaitsForInterviewsAndWithdrawnUserIsNotSent() {
        tx.executeWithoutResult(status -> {
            users.findByUuid(uuid).orElseThrow().setStatus(UserStatus.ONBOARD_C);
            events.publishEvent(new UserEmbeddingRefreshRequestedEvent(uuid, EmbeddingType.PROFILE));
        });
        assertThat(jobs.findById(cloneId)).isEmpty();
        tx.executeWithoutResult(status -> users.findByUuid(uuid).orElseThrow().setStatus(UserStatus.ACTIVE));
        enqueue();
        tx.executeWithoutResult(status -> users.findByUuid(uuid).orElseThrow().setStatus(UserStatus.INACTIVE));
        assertThat(service.claim(cloneId)).isNull();
    }
}
