package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobVerificationRejectRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
