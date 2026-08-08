package com.mirrorsoul.mirrorsoul_api.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ValueBalanceChosenSide {
    LEFT(-1),
    RIGHT(1);

    private final int scoreValue;
}
