package com.mirrorsoul.mirrorsoul_api.repository;

import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiVoiceProfileRepository extends JpaRepository<AiVoiceProfile, Long> {
    boolean existsByCloneIdAndActiveTrueAndStatus(Long cloneId, String status);

    Optional<AiVoiceProfile> findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(Long cloneId);
}
