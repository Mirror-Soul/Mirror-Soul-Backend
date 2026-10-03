package com.mirrorsoul.mirrorsoul_api.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.VoiceResultQueueProperties;
import com.mirrorsoul.mirrorsoul_api.service.VoiceTrainingResultService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

class VoiceTrainingResultConsumerTest {
    private final SqsClient sqs = mock(SqsClient.class);
    private final VoiceTrainingResultService service = mock(VoiceTrainingResultService.class);

    private VoiceTrainingResultConsumer consumer(String body) {
        var properties = new VoiceResultQueueProperties();
        properties.setQueueUrl("https://sqs.ap-northeast-2.amazonaws.com/123456789012/voice-results");
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().messageId("message-1").receiptHandle("receipt").body(body).build()).build());
        return new VoiceTrainingResultConsumer(sqs, new ObjectMapper(), properties, service);
    }

    @Test
    void acknowledgesOnlyAfterServiceReturns() {
        consumer("{\"eventType\":\"VOICE_TRAINING_STATUS\",\"status\":\"PROCESSING\"}").poll();
        var order = inOrder(service, sqs);
        order.verify(service).handle(any());
        order.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void failureOrBadJsonLeavesMessageForRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(service).handle(any());
        consumer("{}").poll();
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        reset(service, sqs);
        consumer("not json").poll();
        verifyNoInteractions(service);
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
