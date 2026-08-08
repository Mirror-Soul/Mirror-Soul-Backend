package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.UserValueAxisScore;
import com.mirrorsoul.mirrorsoul_api.domain.UserValueAxisScoreId;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserValueAxisScoreRepository extends JpaRepository<UserValueAxisScore, UserValueAxisScoreId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select score from UserValueAxisScore score where score.user.id = :userId and score.axis = :axis")
    Optional<UserValueAxisScore> findByUserIdAndAxisForUpdate(
            @Param("userId") Long userId,
            @Param("axis") ValueBalanceAxis axis);
}
