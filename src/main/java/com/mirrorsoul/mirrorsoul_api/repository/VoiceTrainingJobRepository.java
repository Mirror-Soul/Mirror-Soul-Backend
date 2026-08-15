package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceTrainingJobRepository extends JpaRepository<VoiceTrainingJob, Long> {

    long countByUser_UuidAndSource(UUID userUuid, VoiceTrainingJobSource source);

    Optional<VoiceTrainingJob> findFirstByUser_UuidAndSourceOrderByCreatedAtDescIdDesc(
            UUID userUuid,
            VoiceTrainingJobSource source
    );

    boolean existsByUser_IdAndSourceAndCreatedAtAfter(
            Long userId,
            VoiceTrainingJobSource source,
            LocalDateTime createdAfter
    );

    List<VoiceTrainingJob> findTop5ByUser_UuidAndSourceAndVoiceTrainingSentenceIsNotNullOrderByCreatedAtDescIdDesc(
            UUID userUuid,
            VoiceTrainingJobSource source
    );
}
