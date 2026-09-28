package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiVoiceProfileRepository extends JpaRepository<AiVoiceProfile, Long> {
    boolean existsByCloneIdAndActiveTrueAndStatus(Long cloneId, String status);

    boolean existsByCloneIdAndVoiceTrainingJob_IdAndActiveTrueAndStatus(Long cloneId, Long jobId, String status);

    boolean existsByCloneIdAndActiveTrueAndStatusAndVoiceTrainingJob_IdGreaterThan(Long cloneId, String status, Long jobId);

    Optional<AiVoiceProfile> findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(Long cloneId);
}
