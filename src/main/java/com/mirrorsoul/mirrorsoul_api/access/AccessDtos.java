package com.mirrorsoul.mirrorsoul_api.access;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;
public final class AccessDtos {
 private AccessDtos() {}
 public record StaffView(Long staffId,String displayName,String accountState,Set<AdminPermission> permissions,Long version) {}
 public record Reauthenticate(@NotBlank @Size(max=1000) String password) { @Override public String toString() {return "Reauthenticate[REDACTED]";} }
 public record ReauthView(String token,LocalDateTime expiresAt) { @Override public String toString() {return "ReauthView[REDACTED]";} }
 public record Invite(@NotNull Long memberId,@NotBlank @Size(max=100) String displayName,@NotNull Set<@NotNull AdminPermission> permissions,
  @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
 public record InviteView(StaffView staff,String activationToken,LocalDateTime expiresAt) { @Override public String toString() {return "InviteView[REDACTED]";} }
 public record Grant(@NotNull Set<@NotNull AdminPermission> permissions,@NotNull @PositiveOrZero Long version,
  @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
 public record Revoke(@NotNull @PositiveOrZero Long version,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
 public record Activate(@NotBlank @Size(max=100) String token) { @Override public String toString() {return "Activate[REDACTED]";} }
 public record AuditView(Long id,Long staffId,String action,String target,LocalDateTime occurredAt,String result,String reasonCode,String correlationId) {}
}
