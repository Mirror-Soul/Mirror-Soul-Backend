package com.mirrorsoul.mirrorsoul_api.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.FaceResultQueueProperties;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.service.FaceTrainingResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "face-result.enabled", havingValue = "true")
public class FaceTrainingResultConsumer {
    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final FaceResultQueueProperties properties;
    private final FaceTrainingResultService service;

    @Scheduled(fixedDelayString = "${face-result.poll-delay-ms:1000}", scheduler = "faceResultScheduler")
    public void poll() {
        try {
            var response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl()).maxNumberOfMessages(1)
                    .waitTimeSeconds(properties.getWaitTimeSeconds())
                    .visibilityTimeout(properties.getVisibilityTimeoutSeconds()).build());
            for (Message message : response.messages()) process(message);
        } catch (RuntimeException exception) {
            log.error("Could not poll face result queue", exception);
        }
    }

    private void process(Message message) {
        try {
            service.handle(mapper.readValue(message.body(), FaceTrainingResultDTO.class));
            // The proxied service has committed before acknowledgment. On failure keep the message
            // for visibility-timeout retry and eventual queue redrive to the DLQ.
            sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.getQueueUrl())
                    .receiptHandle(message.receiptHandle()).build());
        } catch (Exception exception) {
            log.error("Face result was not acknowledged. messageId={}", message.messageId(), exception);
        }
    }
}
