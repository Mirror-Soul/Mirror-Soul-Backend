package com.mirrorsoul.mirrorsoul_api.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="member_deletion_jobs",uniqueConstraints=@UniqueConstraint(columnNames="member_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MemberDeletionJob {
    public enum State { PENDING, PROCESSING, COMPLETED, FAILED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Column(name="member_uuid",nullable=false,length=36) private String memberUuid;
    @Enumerated(EnumType.STRING) @Column(name="backend_state",nullable=false,length=20) private State backendState;
    @Enumerated(EnumType.STRING) @Column(name="ai_state",nullable=false,length=20) private State aiState;
    @Column(name="backend_attempts",nullable=false) private int backendAttempts;
    @Column(name="ai_attempts",nullable=false) private int aiAttempts;
    @Column(name="backend_attempt_limit",nullable=false) private int backendAttemptLimit;
    @Column(name="ai_attempt_limit",nullable=false) private int aiAttemptLimit;
    @Column(name="backend_error",length=500) private String backendError;
    @Column(name="ai_error",length=500) private String aiError;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="ai_lease_until") private LocalDateTime aiLeaseUntil;
    public MemberDeletionJob(User user,LocalDateTime now) {
        memberId=user.getId(); memberUuid=user.getUuid().toString();
        backendAttemptLimit=3; aiAttemptLimit=3;
        backendState=State.PENDING; aiState=State.PENDING; createdAt=now; updatedAt=now;
    }
    public UUID userUuid() { return UUID.fromString(memberUuid); }
    public void backendComplete(LocalDateTime now) {
        backendAttempts++; backendState=State.COMPLETED; backendError=null; updatedAt=now;
    }
    public void backendFailed(LocalDateTime now,String error) {
        backendAttempts++; backendState=State.FAILED; backendError=error; updatedAt=now;
    }
    public boolean claimable(LocalDateTime now) {
        return aiAttempts < aiAttemptLimit && (aiState==State.PENDING || aiState==State.FAILED
                || (aiState==State.PROCESSING && aiLeaseUntil!=null && !now.isBefore(aiLeaseUntil)));
    }
    public void retry(LocalDateTime now) {
        if (backendState!=State.FAILED && aiState!=State.FAILED) {
            throw new IllegalStateException("There is no failed deletion stage.");
        }
        if (backendState==State.FAILED) {
            backendAttemptLimit=Math.addExact(backendAttempts,3);
            backendState=State.PENDING; backendError=null;
        }
        if (aiState==State.FAILED) {
            aiAttemptLimit=Math.addExact(aiAttempts,3);
            aiState=State.PENDING; aiError=null;
        }
        updatedAt=now;
    }
    public void claim(LocalDateTime now) {
        aiAttempts++; aiState=State.PROCESSING; aiLeaseUntil=now.plusHours(1); updatedAt=now;
    }
    public void report(State state,String error,LocalDateTime now) {
        aiState=state; aiError=error; aiLeaseUntil=null; updatedAt=now;
    }
}
