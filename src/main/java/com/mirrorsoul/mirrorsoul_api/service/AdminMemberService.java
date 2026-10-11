package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.member.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminMemberService {
    private final UserRepository users;
    private final MemberActionHistoryRepository history;
    private final MemberDeletionService deletion;

    public Page<MemberView> list(Long actorId, String search, String accountStatus,
            UserStatus onboardingStage, int page, int size) {
        Pageable pageable = pageable(page, size);
        if (onboardingStage != null && !normalStatuses().contains(onboardingStage)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "Invalid onboarding stage.");
        }
        List<UserStatus> statuses = accountStatuses(accountStatus);
        String term = search == null ? "" : search.strip();
        if (term.length() > 255) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        Page<MemberView> result = users.findAll((root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (!term.isEmpty()) {
                String escaped = term.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
                List<Predicate> searches = new ArrayList<>();
                searches.add(cb.like(cb.lower(root.get("email")), "%" + escaped + "%", '!'));
                try { searches.add(cb.equal(root.get("id"), Long.parseLong(term))); }
                catch (NumberFormatException ignored) { }
                try { searches.add(cb.equal(root.get("uuid"), UUID.fromString(term))); }
                catch (IllegalArgumentException ignored) { }
                conditions.add(cb.or(searches.toArray(Predicate[]::new)));
            }
            if (statuses != null) conditions.add(root.get("status").in(statuses));
            if (onboardingStage != null) {
                conditions.add(cb.or(
                    cb.equal(root.get("status"), onboardingStage),
                    cb.and(cb.equal(root.get("status"), UserStatus.SUSPENDED),
                        cb.equal(root.get("statusBeforeSuspension"), onboardingStage)),
                    cb.and(root.get("status").in(UserStatus.INACTIVE, UserStatus.WITHDRAWAL_CONFIRMED, UserStatus.DELETED),
                        cb.or(cb.equal(root.get("statusBeforeWithdrawal"), onboardingStage),
                            cb.and(cb.equal(root.get("statusBeforeWithdrawal"), UserStatus.SUSPENDED),
                                cb.equal(root.get("statusBeforeSuspension"), onboardingStage))))));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        }, pageable).map(MemberView::from);
        log.info("Admin member list viewed actorId={} page={} size={}", actorId, page, size);
        return result;
    }

    public MemberView detail(Long actorId, Long id) {
        MemberView result = MemberView.from(member(id));
        log.info("Admin member viewed actorId={} memberId={}", actorId, id);
        return result;
    }

    public Page<MemberActionResponse> history(Long actorId, Long id, int page, int size) {
        member(id);
        log.info("Admin member history viewed actorId={} memberId={}", actorId, id);
        return history.findByMemberId(id, pageable(page,size)).map(MemberActionResponse::from);
    }

    @Transactional
    public MemberActionResponse act(Long actorId, Long id, MemberActionRequest request) {
        User actor = member(actorId);
        if (actor.getStatus() != UserStatus.ACTIVE || (actor.getRole() != UserRole.ADMIN
                && actor.getRole() != UserRole.SUPER_ADMIN)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        if (Objects.equals(actorId, id)) throw new GeneralException(GeneralErrorCode.FORBIDDEN, "Self actions are not allowed.");
        if (request.action() == MemberAction.CONFIRM_WITHDRAWAL && actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "Super admin required.");
        }
        User user = users.findByIdForUpdate(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.USER && actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN,"Super admin required to change administrator accounts.");
        }
        Optional<MemberActionHistory> previous = history.findByMemberIdAndIdempotencyKey(id, request.idempotencyKey());
        if (previous.isPresent()) {
            MemberActionHistory h = previous.get();
            if (!Objects.equals(h.getActorId(), actorId) || h.getAction() != request.action()
                    || h.getReasonCode() != request.reasonCode() || !Objects.equals(h.getExpectedVersion(), request.expectedVersion())) {
                throw new GeneralException(GeneralErrorCode.MEMBER_IDEMPOTENCY_CONFLICT);
            }
            return MemberActionResponse.from(h);
        }
        if (!Objects.equals(user.getVersion(), request.expectedVersion())) {
            throw new GeneralException(GeneralErrorCode.MEMBER_VERSION_CONFLICT);
        }
        validateReason(request);
        UserStatus before = user.getStatus();
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        try {
            switch (request.action()) {
                case SUSPEND -> user.suspend();
                case UNSUSPEND -> user.unsuspend();
                case WITHDRAW -> user.deactivate(now);
                case RECOVER -> {
                    if (!user.canRecover(now)) throw new IllegalStateException("Recovery period expired.");
                    user.reactivate();
                }
                case RETRY_DELETION -> {
                    if (user.getStatus()!=UserStatus.WITHDRAWAL_CONFIRMED && user.getStatus()!=UserStatus.DELETED) {
                        throw new IllegalStateException("Withdrawal is not confirmed.");
                    }
                    deletion.retry(user,now);
                }
                case CONFIRM_WITHDRAWAL -> {
                    user.confirmWithdrawal();
                    deletion.enqueue(user, now);
                }
            }
        } catch (IllegalStateException e) {
            throw new GeneralException(GeneralErrorCode.MEMBER_ACTION_CONFLICT, e.getMessage());
        }
        users.flush();
        MemberActionHistory saved = history.saveAndFlush(new MemberActionHistory(id, actorId, request.action(),
                request.reasonCode(), before, user.getStatus(), request.expectedVersion(), user.getVersion(), request.idempotencyKey(), now));
        log.info("Admin member action actorId={} memberId={} action={} historyId={}", actorId, id, request.action(), saved.getId());
        return MemberActionResponse.from(saved);
    }

    private void validateReason(MemberActionRequest r) {
        boolean valid = switch (r.action()) {
            case SUSPEND -> Set.of(MemberReasonCode.POLICY_VIOLATION, MemberReasonCode.SECURITY_RISK, MemberReasonCode.ADMIN_CORRECTION).contains(r.reasonCode());
            case UNSUSPEND -> Set.of(MemberReasonCode.REVIEW_COMPLETED, MemberReasonCode.ADMIN_CORRECTION).contains(r.reasonCode());
            case RETRY_DELETION -> Set.of(MemberReasonCode.REVIEW_COMPLETED, MemberReasonCode.ADMIN_CORRECTION).contains(r.reasonCode());
            case WITHDRAW, RECOVER, CONFIRM_WITHDRAWAL -> Set.of(MemberReasonCode.USER_REQUEST, MemberReasonCode.ADMIN_CORRECTION).contains(r.reasonCode());
        };
        if (!valid) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "Reason is not valid for this action.");
    }
    private User member(Long id) {
        return users.findById(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }
    private List<UserStatus> normalStatuses() {
        return List.of(UserStatus.ONBOARD_A, UserStatus.ONBOARD_B, UserStatus.ONBOARD_C, UserStatus.ONBOARD_D, UserStatus.ACTIVE);
    }
    private List<UserStatus> accountStatuses(String s) {
        if (s == null) return null;
        return switch(s) {
            case "NORMAL" -> normalStatuses();
            case "SUSPENDED" -> List.of(UserStatus.SUSPENDED);
            case "WITHDRAWAL_PENDING" -> List.of(UserStatus.INACTIVE);
            case "WITHDRAWAL_CONFIRMED" -> List.of(UserStatus.WITHDRAWAL_CONFIRMED, UserStatus.DELETED);
            default -> throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "Invalid account status.");
        };
    }
    public static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
