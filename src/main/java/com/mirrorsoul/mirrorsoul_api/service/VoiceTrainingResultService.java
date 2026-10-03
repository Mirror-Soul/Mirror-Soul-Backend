package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.CloneSimilarityCalculator;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.dto.voice.VoiceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceTrainingResultService {
    private final VoiceTrainingJobRepository jobs;
    private final CloneRepository clones;
    private final AiVoiceProfileRepository profiles;
    private final AwsS3Properties storage;
    private final CloneReadinessService readiness;

    @Transactional(timeout = 60)
    public void handle(VoiceTrainingResultDTO message) {
        require(message != null && "VOICE_TRAINING_STATUS".equals(message.eventType()), "Invalid eventType");
        require(message.jobId() != null && message.userUuid() != null,
                "Missing result identifiers");
        require(message.status() != null, "Missing result status");
        // Use the same lock order as the other clone result writers.
        Clone clone = clones.findByUserUuidForUpdate(message.userUuid())
                .orElseThrow(() -> new IllegalArgumentException("Unknown clone"));
        require(message.cloneId() == null || Objects.equals(clone.getId(), message.cloneId()),
                "Result clone mismatch");
        VoiceTrainingJob job = jobs.findLockedById(message.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown voice job"));
        require(Objects.equals(clone.getUser().getUuid(), message.userUuid())
                && Objects.equals(job.getUser().getUuid(), message.userUuid()), "Result owner mismatch");
        if (job.isTerminal()) return;
        switch (message.status()) {
            case "PROCESSING" -> job.markProcessing();
            case "FAILED" -> {
                var error = message.error();
                require(error != null && error.retryable() != null, "Missing failure/retryable");
                require(error.code() == null || error.code().length() <= 100, "Error code too long");
                job.recordFailure(error.code(), error.message(), error.retryable());
            }
            case "COMPLETED" -> complete(message, clone, job);
            default -> throw new IllegalArgumentException("Invalid result status");
        }
    }

    private void complete(VoiceTrainingResultDTO message, Clone clone, VoiceTrainingJob job) {
        var result = message.result();
        require(result != null, "Missing completion result");
        require(result.elevenlabsVoiceId() != null && !result.elevenlabsVoiceId().isBlank()
                && result.elevenlabsVoiceId().length() <= 255, "Invalid voice ID");
        require(result.voiceScore() != null, "Missing voice score");
        CloneSimilarityCalculator.validateScore(result.voiceScore());
        validateIntroAudio(result.introAudio(), message);

        var activeProfiles = profiles.findAllByCloneIdAndActiveTrue(clone.getId());
        boolean newerActive = activeProfiles.stream()
                .anyMatch(profile -> profile.getVoiceTrainingJob().getId() > job.getId());
        boolean newerScore = clone.getSimilarityVoiceJobId() != null
                && clone.getSimilarityVoiceJobId() > job.getId();
        boolean activate = !newerActive && !newerScore;

        // A profile written by the old worker during rollout can already exist for this job.
        if (!profiles.existsByVoiceTrainingJob_Id(job.getId())) {
            AiVoiceProfile profile = AiVoiceProfile.create(clone, job, result.elevenlabsVoiceId(),
                    "ACTIVE", activate);
            if (result.introAudio() != null) {
                var audio = result.introAudio();
                profile.setIntroAudio(audio.bucket(), audio.objectKey(), audio.contentType(),
                        audio.sizeBytes(), audio.durationMs());
            }
            if (activate) activeProfiles.forEach(AiVoiceProfile::deactivate);
            profiles.saveAndFlush(profile);
        } else {
            // Existing rows remain the source of truth during the mixed-version rollout.
            activate = profiles.existsByCloneIdAndVoiceTrainingJob_IdAndActiveTrueAndStatus(
                    clone.getId(), job.getId(), "ACTIVE") && !newerActive && !newerScore;
        }
        if (activate) clone.updateVoiceSimilarity(job.getId(), result.voiceScore());
        job.complete();
        readiness.refreshLocked(clone);
    }

    private void validateIntroAudio(VoiceTrainingResultDTO.IntroAudio audio, VoiceTrainingResultDTO message) {
        if (audio == null) return;
        require(Objects.equals(storage.getBucket(), audio.bucket()), "Invalid intro audio bucket");
        require(audio.objectKey() != null
                && audio.objectKey().startsWith("voice-intros/" + message.userUuid() + "/")
                && audio.objectKey().endsWith(".mp3") && audio.objectKey().length() <= 500,
                "Invalid intro audio key");
        require("audio/mpeg".equals(audio.contentType()), "Invalid intro audio content type");
        require(audio.sizeBytes() != null && audio.sizeBytes() >= 1 && audio.sizeBytes() <= 5_242_880,
                "Invalid intro audio size");
        require(audio.durationMs() != null && audio.durationMs() >= 1 && audio.durationMs() <= 30_000,
                "Invalid intro audio duration");
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException(reason);
    }
}
