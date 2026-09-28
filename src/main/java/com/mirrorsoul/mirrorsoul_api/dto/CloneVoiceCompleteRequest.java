package com.mirrorsoul.mirrorsoul_api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CloneVoiceCompleteRequest(
        @NotNull @Positive Long jobId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal voiceScore) {}
