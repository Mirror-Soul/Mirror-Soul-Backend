package com.mirrorsoul.mirrorsoul_api.dto.member;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import jakarta.validation.constraints.*;
public record MemberActionRequest(@NotNull MemberAction action, @NotNull MemberReasonCode reasonCode,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String idempotencyKey) {}
