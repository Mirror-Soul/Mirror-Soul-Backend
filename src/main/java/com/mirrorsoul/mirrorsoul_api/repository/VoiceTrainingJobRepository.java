package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VoiceTrainingJobSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface VoiceTrainingJobRepository extends JpaRepository<VoiceTrainingJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from VoiceTrainingJob j where j.id = :id")
    Optional<VoiceTrainingJob> findLockedById(@Param("id") Long id);

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
