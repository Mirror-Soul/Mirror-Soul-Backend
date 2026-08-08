package com.mirrorsoul.mirrorsoul_api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserValueAxisScoreTest {

    @Test
    void calculatesRunningAverageFromLeftAndRightSamples() {
        User user = User.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("password")
                .build();
        UserValueAxisScore score = UserValueAxisScore.initialize(user, ValueBalanceAxis.LOVE);

        score.addSample(-1);
        score.addSample(1);
        score.addSample(1);

        assertThat(score.getSampleCount()).isEqualTo(3);
        assertThat(score.getScore()).isEqualByComparingTo(new BigDecimal("0.3333"));
    }
}
