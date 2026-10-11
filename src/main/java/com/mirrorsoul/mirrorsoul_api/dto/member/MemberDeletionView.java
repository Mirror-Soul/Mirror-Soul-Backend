package com.mirrorsoul.mirrorsoul_api.dto.member;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob;
import com.mirrorsoul.mirrorsoul_api.domain.MemberDeletionJob.State;
import java.time.LocalDateTime;
public record MemberDeletionView(Long jobId,Long memberId,String memberUuid,State backendState,State aiState,
        int backendAttempts,int aiAttempts,String backendError,String aiError,
        LocalDateTime createdAt,LocalDateTime updatedAt,LocalDateTime aiLeaseUntil,boolean complete) {
    public static MemberDeletionView from(MemberDeletionJob j) {
        return new MemberDeletionView(j.getId(),j.getMemberId(),j.getMemberUuid(),j.getBackendState(),j.getAiState(),
            j.getBackendAttempts(),j.getAiAttempts(),j.getBackendError(),j.getAiError(),j.getCreatedAt(),j.getUpdatedAt(),
            j.getAiLeaseUntil(),j.getBackendState()==State.COMPLETED && j.getAiState()==State.COMPLETED);
    }
}
