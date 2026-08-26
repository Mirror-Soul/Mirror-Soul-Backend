package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.ValueBalanceQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValueBalanceQuestionRepository extends JpaRepository<ValueBalanceQuestion, Long> {
    Optional<ValueBalanceQuestion> findByIdAndActiveTrue(Long id);

    @Query("""
            select question from ValueBalanceQuestion question
            where question.active = true
              and not exists (
                  select answer.id from ValueBalanceAnswer answer
                  where answer.user.id = :userId
                    and answer.question.id = question.id
              )
            """)
    List<ValueBalanceQuestion> findActiveNeverAnswered(@Param("userId") Long userId);
}
