package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VoiceTrainingSentenceRepository extends JpaRepository<VoiceTrainingSentence, Long> {

    @Query(value = """
            SELECT *
            FROM voice_training_sentences
            WHERE is_active = TRUE
            ORDER BY RAND()
            LIMIT 1
            """, nativeQuery = true)
    Optional<VoiceTrainingSentence> findRandomActive();

    @Query(value = """
            SELECT *
            FROM voice_training_sentences
            WHERE is_active = TRUE
              AND id NOT IN (:excludedIds)
            ORDER BY RAND()
            LIMIT 1
            """, nativeQuery = true)
    Optional<VoiceTrainingSentence> findRandomActiveExcluding(List<Long> excludedIds);

    Optional<VoiceTrainingSentence> findByIdAndActiveTrue(Long id);
}
