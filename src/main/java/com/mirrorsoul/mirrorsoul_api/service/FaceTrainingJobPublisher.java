package com.mirrorsoul.mirrorsoul_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.config.AwsSqsProperties;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJobFile;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingMessageDTO;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.FaceTrainingJobRepository;
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
public class FaceTrainingJobPublisher {

    private final FaceTrainingJobRepository faceTrainingJobRepository;
    private final CloneRepository cloneRepository;
    private final AwsS3Properties awsS3Properties;
    private final AwsSqsProperties awsSqsProperties;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long faceTrainingJobId) {
        FaceTrainingJob job = faceTrainingJobRepository.findById(faceTrainingJobId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FaceTrainingJob not found: " + faceTrainingJobId
                ));
        Clone clone = cloneRepository.findByUserUuid(job.getUser().getUuid())
                .orElseThrow(() -> new IllegalStateException(
                        "Clone not found for user: " + job.getUser().getUuid()
                ));

        List<String> objectKeys = job.getFiles().stream()
                .map(FaceTrainingJobFile::getObjectKey)
                .toList();
        FaceTrainingMessageDTO message = FaceTrainingMessageDTO.of(
                job.getId(),
                job.getSource().name(),
                job.getUser().getUuid(),
                clone.getId(),
                awsS3Properties.getBucket(),
                objectKeys
        );

        try {
            String messageBody = objectMapper.writeValueAsString(message);
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(awsSqsProperties.getFaceTrainingQueueUrl())
                    .messageBody(messageBody)
                    .build());

            job.markMessageSent(response.messageId());
            log.info("Published face training job. jobId={}, sqsMessageId={}",
                    job.getId(), response.messageId());
        } catch (JsonProcessingException | SqsException | SdkClientException e) {
            job.markDispatchFailed(e.getMessage());
            log.error("Failed to publish face training job. jobId={}", job.getId(), e);
        }
    }
}
