package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "user_value_axis_scores")
@IdClass(UserValueAxisScoreId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserValueAxisScore {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_value_axis_scores_user"))
    private User user;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValueBalanceAxis axis;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score = BigDecimal.ZERO.setScale(4);

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private UserValueAxisScore(User user, ValueBalanceAxis axis) {
        this.user = user;
        this.axis = axis;
    }

    public static UserValueAxisScore initialize(User user, ValueBalanceAxis axis) {
        return new UserValueAxisScore(user, axis);
    }

    public void addSample(int value) {
        BigDecimal weightedSum = score.multiply(BigDecimal.valueOf(sampleCount))
                .add(BigDecimal.valueOf(value));
        sampleCount++;
        score = weightedSum.divide(BigDecimal.valueOf(sampleCount), 4, RoundingMode.HALF_UP);
    }
}
