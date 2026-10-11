package com.mirrorsoul.mirrorsoul_api.repository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob.State;
import com.mirrorsoul.mirrorsoul_api.service.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import(MemberDeletionService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class MemberDeletionTransactionTest {
    @Autowired UserRepository users;
    @Autowired MemberDeletionJobRepository jobs;
    @Autowired MemberDeletionService deletion;
    @MockitoBean JobVerificationEvidenceCleanupService evidence;
    @Test void failedCleanupRollsBackAndFailureRecordSurvivesInSeparateTransaction() {
        LocalDateTime now=LocalDateTime.now();
        User user=User.builder().email("cleanup-failure@example.com").passwordHash("hash")
            .status(UserStatus.ACTIVE).createdAt(now).build();
        user.deactivate(now);user.confirmWithdrawal();
        user=users.saveAndFlush(user);
        MemberDeletionJob job=jobs.saveAndFlush(new MemberDeletionJob(user,now));
        doThrow(new IllegalStateException("storage unavailable")).when(evidence).deleteForUsers(List.of(user.getId()));
        Long id=job.getId();
        assertThatThrownBy(() -> deletion.cleanBackend(id,now)).isInstanceOf(IllegalStateException.class);
        deletion.recordFailure(id,now);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.WITHDRAWAL_CONFIRMED);
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo("cleanup-failure@example.com");
        assertThat(jobs.findById(id).orElseThrow().getBackendState()).isEqualTo(State.FAILED);
        assertThat(jobs.findById(id).orElseThrow().getBackendAttempts()).isEqualTo(1);
        jobs.deleteById(id);users.deleteById(user.getId());
    }
}
