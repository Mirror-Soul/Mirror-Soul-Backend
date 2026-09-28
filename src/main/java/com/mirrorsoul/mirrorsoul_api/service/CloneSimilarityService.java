package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.CloneSimilarityCalculator;
import com.mirrorsoul.mirrorsoul_api.dto.ClonePersonalityCompleteRequest;
import com.mirrorsoul.mirrorsoul_api.dto.CloneVoiceCompleteRequest;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CloneSimilarityService {
    private final CloneRepository clones;
    private final VoiceTrainingJobRepository voiceJobs;
    private final AiVoiceProfileRepository voices;
    private final RagProfileJobRepository ragJobs;
    private final CloneReadinessService readiness;

    @Transactional
    public void completePersonality(Long cloneId, ClonePersonalityCompleteRequest request) {
        require(CloneSimilarityCalculator.VERSION.equals(request.calculationVersion()), "Unsupported calculation version");
        Clone clone = findLocked(cloneId);
        if (request.sourceRevision() != null) {
            // Same lock order as RAG request registration: clone, then outbox.
            var job = ragJobs.findLockedById(cloneId)
                    .orElseThrow(() -> invalid("Unknown RAG revision"));
            if (request.sourceRevision() < job.getRequestedRevision()) return;
            require(request.sourceRevision() == job.getRequestedRevision(), "Unknown RAG revision");
        }
        if (!clone.updateProfileSimilarity(request.sourceRevision(), request.profileScore(),
                request.dataReliabilityScore(), request.penaltyScore())) return;
        clone.updatePersonalityTrainingCompleted(true);
        readiness.refreshLocked(clone);
    }

    @Transactional
    public void completeVoice(Long cloneId, CloneVoiceCompleteRequest request) {
        Clone clone = findLocked(cloneId);
        var job = voiceJobs.findById(request.jobId()).orElseThrow(() -> invalid("Unknown voice job"));
        require(Objects.equals(job.getUser().getId(), clone.getUser().getId()), "Voice result owner mismatch");
        if (clone.getSimilarityVoiceJobId() != null && request.jobId() <= clone.getSimilarityVoiceJobId()) return;
        if (voices.existsByCloneIdAndActiveTrueAndStatusAndVoiceTrainingJob_IdGreaterThan(
                cloneId, "ACTIVE", request.jobId())) return;
        // The existing voice worker owns voice-profile persistence. Only accept its committed active result.
        require(voices.existsByCloneIdAndVoiceTrainingJob_IdAndActiveTrueAndStatus(
                cloneId, request.jobId(), "ACTIVE"), "An active voice profile for this job is required");
        clone.updateVoiceSimilarity(request.jobId(), request.voiceScore());
        readiness.refreshLocked(clone);
    }

    private Clone findLocked(Long cloneId) {
        return clones.findLockedById(cloneId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CLONE_NOT_FOUND));
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw invalid(message);
    }

    private static GeneralException invalid(String message) {
        return new GeneralException(GeneralErrorCode.INVALID_PARAMETER, message);
    }
}
