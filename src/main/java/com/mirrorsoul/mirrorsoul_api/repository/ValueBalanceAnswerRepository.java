package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceAnswer;
import com.mirrorsoul.mirrorsoul_api.domain.enums.ValueBalanceAxis;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValueBalanceAnswerRepository extends JpaRepository<ValueBalanceAnswer, Long> {
    long countByUserIdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
            Long userId, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndQuestionIdAndAnsweredAtGreaterThanEqual(
            Long userId, Long questionId, LocalDateTime answeredSince);

    @Query("""
            select distinct answer.question.axis from ValueBalanceAnswer answer
            where answer.user.id = :userId
              and answer.answeredAt >= :from and answer.answeredAt < :to
            """)
    Set<ValueBalanceAxis> findAnsweredAxes(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
