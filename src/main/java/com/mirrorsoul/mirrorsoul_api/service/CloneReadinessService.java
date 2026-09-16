package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloneReadinessService {
    private final CloneRepository clones;
    private final AiVoiceProfileRepository voices;
    private final AiFaceProfileRepository faces;

    @Transactional
    public void refresh(Long cloneId) {
        clones.findLockedById(cloneId).ifPresent(this::refreshLocked);
    }

    // Caller must hold the clone row lock within the same transaction.
    public void refreshLocked(Clone clone) {
        clone.refreshReadiness(
                voices.existsByCloneIdAndActiveTrueAndStatus(clone.getId(), "ACTIVE"),
                faces.existsByCloneIdAndActiveTrueAndStatus(clone.getId(), "READY"));
    }

    @Transactional
    public void updatePersonalityTraining(Long cloneId, boolean completed) {
        Clone clone = clones.findLockedById(cloneId)
                .orElseThrow(() -> new IllegalArgumentException("Clone not found: " + cloneId));
        clone.updatePersonalityTrainingCompleted(completed);
        refreshLocked(clone);
    }
}
