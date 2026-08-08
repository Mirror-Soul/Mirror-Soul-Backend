package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import java.io.Serializable;
import java.util.Objects;

public class UserValueAxisScoreId implements Serializable {
    private Long user;
    private ValueBalanceAxis axis;

    public UserValueAxisScoreId() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserValueAxisScoreId that)) return false;
        return Objects.equals(user, that.user) && axis == that.axis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, axis);
    }
}
