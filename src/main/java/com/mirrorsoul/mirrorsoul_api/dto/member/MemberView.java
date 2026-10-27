package com.mirrorsoul.mirrorsoul_api.dto.member;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import java.time.LocalDateTime;
import java.util.UUID;
public record MemberView(Long id, UUID uuid, String name, String maskedEmail, Job job,
        UserStatus onboardingStage, String accountStatus, Long version,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime withdrawnAt,
        LocalDateTime recoveryDeadline, LocalDateTime deletedAt) {
    public static MemberView from(User u) {
        String email = u.getEmail();
        int at = email == null ? -1 : email.indexOf('@');
        String masked = at < 1 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
        String account = switch (u.getStatus()) {
            case SUSPENDED -> "SUSPENDED";
            case INACTIVE -> "WITHDRAWAL_PENDING";
            case WITHDRAWAL_CONFIRMED, DELETED -> "WITHDRAWAL_CONFIRMED";
            default -> "NORMAL";
        };
        return new MemberView(u.getId(), u.getUuid(), u.getName(), masked, u.getJob(),
                u.onboardingStatus(), account, u.getVersion(), u.getCreatedAt(), u.getUpdatedAt(),
                u.getWithdrawnAt(), u.getStatus() == UserStatus.INACTIVE && u.getWithdrawnAt() != null
                        ? u.getWithdrawnAt().plusDays(30) : null, u.getDeletedAt());
    }
}
