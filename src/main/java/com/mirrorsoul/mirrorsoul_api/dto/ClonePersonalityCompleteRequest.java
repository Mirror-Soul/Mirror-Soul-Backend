package com.mirrorsoul.mirrorsoul_api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ClonePersonalityCompleteRequest(
        @NotBlank @Pattern(regexp = "clone-similarity-v1") String calculationVersion,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal profileScore,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal dataReliabilityScore,
        @NotNull @DecimalMin("0") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal penaltyScore,
        @Positive Long sourceRevision) {}
