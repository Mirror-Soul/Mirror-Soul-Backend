package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FaceTrainingResultService {
    private final FaceTrainingJobRepository jobs;
    private final CloneRepository clones;
    private final AiFaceProfileRepository profiles;
    private final AwsS3Properties storage;
    private final CloneReadinessService readiness;

    @Transactional(timeout = 60)
    public void handle(FaceTrainingResultDTO message) {
        require(message != null && "FACE_PROFILE_BUILD_STATUS".equals(message.eventType()), "Invalid eventType");
        require(message.jobId() != null && message.cloneId() != null && message.userUuid() != null,
                "Missing result identifiers");
        require(List.of("PROCESSING", "COMPLETED", "FAILED").contains(
                message.status() == null ? "" : message.status()), "Invalid result status");
        // Serialize all profile activations for a clone, including different jobs.
        Clone clone = clones.findLockedById(message.cloneId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown clone"));
        FaceTrainingJob job = jobs.findLockedById(message.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown face job"));
        require(Objects.equals(job.getUser().getUuid(), message.userUuid())
                && Objects.equals(clone.getUser().getUuid(), message.userUuid()), "Result owner mismatch");
        // A committed terminal result is immutable, including on SQS redelivery.
        if (job.isTerminal()) return;
        switch (message.status()) {
            case "PROCESSING" -> job.markProcessing();
            case "FAILED" -> {
                var error = message.error();
                require(error != null && error.retryable() != null, "Missing failure/retryable");
                require(error.code() == null || error.code().length() <= 100, "Error code too long");
                job.recordFailure(error.code(), error.message(), Boolean.TRUE.equals(error.retryable()));
            }
            case "COMPLETED" -> complete(message, clone, job);
            default -> throw new IllegalArgumentException("Invalid result status");
        }
    }

    private void complete(FaceTrainingResultDTO message, Clone clone, FaceTrainingJob job) {
        var result = message.result();
        require(result != null && result.qualityGatePassed() != null, "Missing completion result");
        if (!Boolean.TRUE.equals(result.qualityGatePassed())) {
            job.recordFailure("QUALITY_GATE_FAILED", "Face quality gate did not pass", false);
            return;
        }
        require("READY_FOR_RENDERING".equals(result.profileStatus()), "Invalid profile status");
        var artifacts = result.artifacts();
        require(artifacts != null && Objects.equals(storage.getBucket(), artifacts.bucket()), "Invalid result bucket");
        String prefix = "face-results/" + message.userUuid() + "/job-" + job.getId() + "/";
        require((prefix + "face-profile.json").equals(artifacts.profileKey()), "Invalid profile key");
        require((prefix + "portrait.jpg").equals(artifacts.portraitKey()), "Invalid portrait key");
        require((prefix + "preprocess-manifest.json").equals(artifacts.manifestKey()), "Invalid manifest key");
        require(artifacts.previewKey() == null || (prefix + "preview.mp4").equals(artifacts.previewKey()),
                "Invalid preview key");
        AiFaceProfile profile = AiFaceProfile.ready(clone, job, artifacts.bucket(), artifacts.profileKey(),
                artifacts.portraitKey(), artifacts.manifestKey(), artifacts.previewKey());
        var activeProfiles = profiles.findAllByCloneIdAndActiveTrue(clone.getId());
        boolean newerProfileExists = activeProfiles.stream()
                .anyMatch(existing -> existing.getFaceTrainingJob().getId() > job.getId());
        if (newerProfileExists) profile.deactivate();
        else activeProfiles.forEach(AiFaceProfile::deactivate);
        profiles.saveAndFlush(profile);
        job.complete();
        readiness.refreshLocked(clone);
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException(reason);
    }
}
