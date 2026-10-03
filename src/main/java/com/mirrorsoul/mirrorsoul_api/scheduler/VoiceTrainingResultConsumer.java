package com.mirrorsoul.mirrorsoul_api.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.VoiceResultQueueProperties;
import com.mirrorsoul.mirrorsoul_api.dto.voice.VoiceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.service.VoiceTrainingResultService;
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
@ConditionalOnProperty(name = "voice-result.enabled", havingValue = "true")
public class VoiceTrainingResultConsumer {
    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final VoiceResultQueueProperties properties;
    private final VoiceTrainingResultService service;

    @Scheduled(fixedDelayString = "${voice-result.poll-delay-ms:1000}", scheduler = "voiceResultScheduler")
    public void poll() {
        try {
            var response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl()).maxNumberOfMessages(1)
                    .waitTimeSeconds(properties.getWaitTimeSeconds())
                    .visibilityTimeout(properties.getVisibilityTimeoutSeconds()).build());
            for (Message message : response.messages()) process(message);
        } catch (RuntimeException exception) {
            log.error("Could not poll voice result queue", exception);
        }
    }

    private void process(Message message) {
        try {
            service.handle(mapper.readValue(message.body(), VoiceTrainingResultDTO.class));
            // Acknowledgment happens only after the transactional service commits.
            sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.getQueueUrl())
                    .receiptHandle(message.receiptHandle()).build());
        } catch (Exception exception) {
            log.error("Voice result was not acknowledged. messageId={}", message.messageId(), exception);
        }
    }
}
