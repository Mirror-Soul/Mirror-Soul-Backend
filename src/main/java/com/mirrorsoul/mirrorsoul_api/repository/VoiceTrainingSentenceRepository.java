package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingSentence;
import java.util.Optional;
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

    Optional<VoiceTrainingSentence> findByIdAndActiveTrue(Long id);
}
