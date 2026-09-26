package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileHashSource;
import com.mirrorsoul.mirrorsoul_api.config.CloneProfileGenerationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloneProfileSourceHasher {
    private final ObjectMapper objectMapper;
    private final CloneProfileGenerationProperties properties;

    public String hash(CloneProfileGenerationInput input) {
        CloneProfileHashSource source = new CloneProfileHashSource(
                input.selfIntroduction(), input.mbti(), input.mbtiAxisScores(),
                input.interviews(), input.valueBalanceSummary(), input.promptVersion(),
                properties.getInterviewCompactionVersion()
        );
        try {
            byte[] canonicalJson = objectMapper.writeValueAsBytes(source);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash clone profile source", exception);
        }
    }
}
