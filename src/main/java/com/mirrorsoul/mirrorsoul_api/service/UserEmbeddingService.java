package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.config.GeminiEmbeddingProperties;
import com.mirrorsoul.mirrorsoul_api.domain.InterviewRecord;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingClient;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingTextBuilder;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import com.mirrorsoul.mirrorsoul_api.recommendation.UserEmbeddingRepository;
import com.mirrorsoul.mirrorsoul_api.repository.InterviewRecordRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmbeddingService {

    private static final String INPUT_TEMPLATE_VERSION = "v1";

    private final UserRepository userRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final CloneRepository cloneRepository;
    private final EmbeddingTextBuilder textBuilder;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final ObjectProvider<UserEmbeddingRepository> embeddingRepositoryProvider;
    private final GeminiEmbeddingProperties embeddingProperties;

    public void refresh(UUID userUuid, EmbeddingType type) {
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        UserEmbeddingRepository embeddingRepository = embeddingRepositoryProvider.getIfAvailable();
        if (embeddingClient == null || embeddingRepository == null) {
            log.debug("Embedding infrastructure is disabled. userUuid={}, type={}", userUuid, type);
            return;
        }

        User user = userRepository.findByUuid(userUuid).orElse(null);
        if (user == null) {
            log.warn("Embedding source user was not found. userUuid={}, type={}", userUuid, type);
            return;
        }

        Optional<String> input = buildInput(user, type);
        if (input.isEmpty()) {
            log.warn("Embedding source is incomplete. userUuid={}, type={}", userUuid, type);
            return;
        }

        String sourceHash = hash(type, input.get());
        if (embeddingRepository.findSourceHash(userUuid, type)
                .filter(sourceHash::equals)
                .isPresent()) {
            log.debug("Embedding source is unchanged. userUuid={}, type={}", userUuid, type);
            return;
        }

        float[] embedding = embeddingClient.embed(input.get());
        embeddingRepository.upsert(userUuid, type, embedding, sourceHash);
    }

    private Optional<String> buildInput(User user, EmbeddingType type) {
        return switch (type) {
            case JOB -> textBuilder.buildJobText(user);
            case PROFILE -> textBuilder.buildProfileText(user);
            case INTERVIEW -> {
                List<InterviewRecord> records = interviewRecordRepository
                        .findAllByUser_IdOrderByInterview_IdAsc(user.getId());
                yield textBuilder.buildInterviewText(records);
            }
            case CLONE_SUMMARY -> cloneRepository.findByUserUuid(user.getUuid())
                    .flatMap(textBuilder::buildCloneSummaryText);
            case CONVERSATION -> Optional.empty();
        };
    }

    private String hash(EmbeddingType type, String input) {
        String hashSource = INPUT_TEMPLATE_VERSION
                + '\n' + embeddingProperties.getModel()
                + '\n' + type.name()
                + '\n' + input;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(hashSource.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
