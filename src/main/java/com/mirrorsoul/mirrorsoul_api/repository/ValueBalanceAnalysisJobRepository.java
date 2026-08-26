package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnalysisJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValueBalanceAnalysisJobRepository extends JpaRepository<ValueBalanceAnalysisJob, Long> {
    Optional<ValueBalanceAnalysisJob> findFirstByUserIdOrderBySetNumberDesc(Long userId);
    Optional<ValueBalanceAnalysisJob> findFirstByUserIdAndStatusOrderBySetNumberDesc(
            Long userId, com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAnalysisJobStatus status);
    Optional<ValueBalanceAnalysisJob> findFirstByUserIdAndStatusAndSetNumberLessThanOrderBySetNumberDesc(
            Long userId,
            com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAnalysisJobStatus status,
            int setNumber);
}
