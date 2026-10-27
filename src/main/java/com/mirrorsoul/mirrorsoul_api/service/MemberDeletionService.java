package com.mirrorsoul.mirrorsoul_api.service;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob.State;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.member.MemberDeletionView;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Slf4j @Service @RequiredArgsConstructor
public class MemberDeletionService {
    private final MemberDeletionJobRepository jobs;
    private final UserRepository users;
    private final PushDeviceRepository devices;
    private final JobVerificationEvidenceCleanupService evidence;
    @Transactional
    public void enqueue(User user,LocalDateTime now) {
        if (jobs.findByMemberId(user.getId()).isEmpty()) jobs.save(new MemberDeletionJob(user,now));
    }
    @Transactional
    public void retry(User user,LocalDateTime now) {
        Long id=jobs.findIdByMemberId(user.getId())
                .orElseThrow(() -> new IllegalStateException("There is no deletion job."));
        MemberDeletionJob job=jobs.findForUpdate(id).orElseThrow();
        job.retry(now);
    }
    @Transactional(readOnly=true)
    public Optional<MemberDeletionView> status(Long memberId) {
        if (!users.existsById(memberId)) throw new GeneralException(GeneralErrorCode.USER_NOT_FOUND);
        return jobs.findByMemberId(memberId).map(MemberDeletionView::from);
    }
    @Transactional
    public void expireAiLeases(LocalDateTime now) {
        for (MemberDeletionJob candidate : jobs.findByAiStateAndAiLeaseUntilLessThanEqual(State.PROCESSING,now,PageRequest.of(0,100))) {
            MemberDeletionJob job=jobs.findForUpdate(candidate.getId()).orElseThrow();
            if (job.getAiState()==State.PROCESSING && job.getAiLeaseUntil()!=null && !now.isBefore(job.getAiLeaseUntil())) {
                job.report(State.FAILED,"AI cleanup lease expired.",now);
                log.error("AI cleanup lease expired jobId={} attempts={}",job.getId(),job.getAiAttempts());
            }
        }
    }
    @Transactional(readOnly=true)
    public List<Long> pendingBackend() {
        return jobs.findBackendAvailable(List.of(State.PENDING,State.FAILED),
                PageRequest.of(0,100,Sort.by("id"))).stream().map(MemberDeletionJob::getId).toList();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void cleanBackend(Long id,LocalDateTime now) {
        // Lock order matches admin actions (user, then deletion job).
        Long memberId=jobs.findMemberId(id).orElseThrow();
        User user=users.findByIdForUpdate(memberId).orElseThrow();
        MemberDeletionJob job=jobs.findForUpdate(id).orElseThrow();
        if (job.getBackendState()==State.COMPLETED || job.getBackendAttempts()>=job.getBackendAttemptLimit()) return;
        if (user.getStatus()!=UserStatus.WITHDRAWAL_CONFIRMED && user.getStatus()!=UserStatus.DELETED)
            throw new IllegalStateException("Withdrawal must be confirmed.");
        devices.deleteAllByUserUuidIn(List.of(user.getUuid()));
        evidence.deleteForUsers(List.of(user.getId()));
        if (user.getStatus()!=UserStatus.DELETED) user.anonymize(now);
        job.backendComplete(now);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordFailure(Long id,LocalDateTime now) {
        MemberDeletionJob job=jobs.findForUpdate(id).orElseThrow();
        if (job.getBackendState()==State.COMPLETED || job.getBackendAttempts()>=job.getBackendAttemptLimit()) return;
        job.backendFailed(now,"Backend cleanup failed; inspect restricted application logs.");
        log.error("Member deletion failed jobId={} memberId={} attempts={}",id,job.getMemberId(),job.getBackendAttempts());
    }
    @Transactional(readOnly=true)
    public Page<MemberDeletionView> available(int page,int size) {
        return jobs.findAvailable(List.of(State.PENDING,State.FAILED),State.PROCESSING,LocalDateTime.now(),
            AdminMemberService.pageable(page,size)).map(MemberDeletionView::from);
    }
    @Transactional
    public MemberDeletionView claim(Long id) {
        MemberDeletionJob job=jobs.findForUpdate(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.API_NOT_FOUND));
        LocalDateTime now=LocalDateTime.now();
        if (!job.claimable(now)) throw new GeneralException(GeneralErrorCode.MEMBER_ACTION_CONFLICT,"Job cannot be claimed.");
        job.claim(now);
        return MemberDeletionView.from(job);
    }
    @Transactional
    public MemberDeletionView report(Long id,Long memberId,int attempt,State state) {
        if (state!=State.COMPLETED && state!=State.FAILED) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        MemberDeletionJob job=jobs.findForUpdate(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.API_NOT_FOUND));
        if (!Objects.equals(memberId,job.getMemberId()) || attempt!=job.getAiAttempts())
            throw new GeneralException(GeneralErrorCode.MEMBER_ACTION_CONFLICT,"Member or attempt mismatch.");
        if (job.getAiState()==state) return MemberDeletionView.from(job);
        if (job.getAiState()!=State.PROCESSING) throw new GeneralException(GeneralErrorCode.MEMBER_ACTION_CONFLICT);
        job.report(state,state==State.FAILED ? "AI cleanup failed." : null,LocalDateTime.now());
        if (state==State.FAILED) log.error("AI deletion failed jobId={} memberId={} attempt={}",id,memberId,attempt);
        return MemberDeletionView.from(job);
    }
}
