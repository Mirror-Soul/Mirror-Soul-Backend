package com.mirrorsoul.mirrorsoul_api.dto.valuebalance;

import jakarta.validation.constraints.NotBlank;

public record ValueBalanceAnalysisCallbackDTO(@NotBlank String personalitySummary) {
}
