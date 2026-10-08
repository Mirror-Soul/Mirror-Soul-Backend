package com.mirrorsoul.mirrorsoul_api.dto.jobverification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JobVerificationSubmitRequest(
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 500) String> objectKeys
) {
}
