package com.mirrorsoul.mirrorsoul_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.config.AwsSqsProperties;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJobFile;
import com.mirrorsoul.mirrorsoul_api.dto.voice.VoiceTrainingMessageDTO;
import com.mirrorsoul.mirrorsoul_api.repository.VoiceTrainingJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTrainingJobPublisher {

    private final VoiceTrainingJobRepository voiceTrainingJobRepository;
    private final AwsS3Properties awsS3Properties;
    private final AwsSqsProperties awsSqsProperties;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long voiceTrainingJobId) {
        VoiceTrainingJob job = voiceTrainingJobRepository.findById(voiceTrainingJobId)
                .orElseThrow(() -> new IllegalArgumentException("VoiceTrainingJob not found: " + voiceTrainingJobId));

        List<String> objectKeys = job.getFiles().stream()
                .map(VoiceTrainingJobFile::getObjectKey)
                .toList();

        VoiceTrainingMessageDTO message = VoiceTrainingMessageDTO.of(
                job.getSource().name(),
                job.getId(),
                job.getUser().getUuid(),
                awsS3Properties.getBucket(),
                objectKeys
        );

        try {
            String messageBody = objectMapper.writeValueAsString(message);
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(awsSqsProperties.getVoiceTrainingQueueUrl())
                    .messageBody(messageBody)
                    .build());

            job.markMessageSent(response.messageId());
            log.info("Published voice training job. jobId={}, sqsMessageId={}", job.getId(), response.messageId());
        } catch (JsonProcessingException | SqsException | SdkClientException e) {
            job.markDispatchFailed(e.getMessage());
            log.error("Failed to publish voice training job. jobId={}", job.getId(), e);
        }
    }
}
