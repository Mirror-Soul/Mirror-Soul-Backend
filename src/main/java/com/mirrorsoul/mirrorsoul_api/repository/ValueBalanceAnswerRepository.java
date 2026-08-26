package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnswer;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValueBalanceAnswerRepository extends JpaRepository<ValueBalanceAnswer, Long> {
    long countByUserId(Long userId);

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    List<ValueBalanceAnswer> findByUserIdOrderByAnsweredAtAscIdAsc(Long userId, Pageable pageable);

}
