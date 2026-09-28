package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.dto.ClonePersonalityCompleteRequest;
import com.mirrorsoul.mirrorsoul_api.dto.CloneVoiceCompleteRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloneTrainingCallbackService {
    private final CloneReadinessService readiness;
    private final CloneSimilarityService similarity;

    @Value("${clone-training.callback-secret:}")
    private String secret;

    public void completePersonality(Long cloneId, String suppliedSecret) {
        completePersonality(cloneId, suppliedSecret, null);
    }

    public void completePersonality(Long cloneId, String suppliedSecret, ClonePersonalityCompleteRequest request) {
        authenticate(suppliedSecret);
        if (request == null) readiness.updatePersonalityTraining(cloneId, true);
        else similarity.completePersonality(cloneId, request);
    }

    public void completeVoice(Long cloneId, String suppliedSecret, CloneVoiceCompleteRequest request) {
        authenticate(suppliedSecret);
        similarity.completeVoice(cloneId, request);
    }

    private void authenticate(String suppliedSecret) {
        if (secret == null || secret.isBlank() || suppliedSecret == null
                || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }
}
