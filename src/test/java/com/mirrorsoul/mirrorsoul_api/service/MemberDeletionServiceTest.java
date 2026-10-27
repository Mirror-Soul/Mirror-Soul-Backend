package com.mirrorsoul.mirrorsoul_api.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob.State;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
class MemberDeletionServiceTest {
    private final MemberDeletionJobRepository jobs=mock(MemberDeletionJobRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final PushDeviceRepository devices=mock(PushDeviceRepository.class);
    private final JobVerificationEvidenceCleanupService evidence=mock(JobVerificationEvidenceCleanupService.class);
    private final MemberDeletionService service=new MemberDeletionService(jobs,users,devices,evidence);
    private User user() { return User.builder().id(2L).uuid(UUID.randomUUID()).email("a@example.com")
        .passwordHash("hash").status(UserStatus.ACTIVE).build(); }
    @Test void successfulBackendCleanupAnonymizesAndTracksSeparatelyFromAi() {
        User user=user();user.deactivate(LocalDateTime.now());user.confirmWithdrawal();
        MemberDeletionJob job=new MemberDeletionJob(user,LocalDateTime.now());
        when(jobs.findMemberId(1L)).thenReturn(Optional.of(2L));
        when(jobs.findForUpdate(1L)).thenReturn(Optional.of(job));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(user));
        service.cleanBackend(1L,LocalDateTime.now());
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(job.getBackendState()).isEqualTo(State.COMPLETED);
        assertThat(job.getAiState()).isEqualTo(State.PENDING);
        verify(evidence).deleteForUsers(List.of(2L));
        service.cleanBackend(1L,LocalDateTime.now());
        verify(evidence,times(1)).deleteForUsers(any());
    }
    @Test void aiClaimsAreExclusiveAndStaleResultsCannotOverwriteRetry() {
        MemberDeletionJob job=new MemberDeletionJob(user(),LocalDateTime.now());
        when(jobs.findForUpdate(1L)).thenReturn(Optional.of(job));
        service.claim(1L);
        assertThatThrownBy(() -> service.claim(1L)).isInstanceOf(GeneralException.class);
        service.report(1L,2L,1,State.FAILED);
        service.claim(1L);
        assertThatThrownBy(() -> service.report(1L,2L,1,State.COMPLETED)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> service.report(1L,3L,2,State.COMPLETED)).isInstanceOf(GeneralException.class);
        service.report(1L,2L,2,State.COMPLETED);
        service.report(1L,2L,2,State.COMPLETED);
        assertThatThrownBy(() -> service.report(1L,2L,2,State.FAILED)).isInstanceOf(GeneralException.class);
        assertThat(job.getAiState()).isEqualTo(State.COMPLETED);
    }
    @Test void manualRetryKeepsAttemptNumbersMonotonic() {
        User user=user();MemberDeletionJob job=new MemberDeletionJob(user,LocalDateTime.now());
        when(jobs.findForUpdate(1L)).thenReturn(Optional.of(job));
        when(jobs.findIdByMemberId(2L)).thenReturn(Optional.of(1L));
        service.claim(1L);service.report(1L,2L,1,State.FAILED);
        service.retry(user,LocalDateTime.now());service.claim(1L);
        assertThat(job.getAiAttempts()).isEqualTo(2);
        assertThatThrownBy(() -> service.report(1L,2L,1,State.COMPLETED)).isInstanceOf(GeneralException.class);
    }
    @Test void failuresStopAfterThreeAttempts() {
        MemberDeletionJob job=new MemberDeletionJob(user(),LocalDateTime.now());
        when(jobs.findForUpdate(1L)).thenReturn(Optional.of(job));
        for(int attempt=1;attempt<=3;attempt++) { service.claim(1L);service.report(1L,2L,attempt,State.FAILED); }
        assertThatThrownBy(() -> service.claim(1L)).isInstanceOf(GeneralException.class);
    }
}
