package com.mirrorsoul.mirrorsoul_api.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirrorsoul.mirrorsoul_api.config.FaceResultQueueProperties;
import com.mirrorsoul.mirrorsoul_api.service.FaceTrainingResultService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

class FaceTrainingResultConsumerTest {
    private final SqsClient sqs = mock(SqsClient.class);
    private final FaceTrainingResultService service = mock(FaceTrainingResultService.class);

    private FaceTrainingResultConsumer consumer(String body) {
        var properties = new FaceResultQueueProperties();
        properties.setQueueUrl("https://sqs.ap-northeast-2.amazonaws.com/123456789012/results");
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().messageId("message-1").receiptHandle("receipt").body(body).build()).build());
        return new FaceTrainingResultConsumer(sqs, new ObjectMapper(), properties, service);
    }

    @Test
    void acknowledgesOnlyAfterSuccessfulServiceReturn() {
        consumer("{\"eventType\":\"FACE_PROFILE_BUILD_STATUS\",\"status\":\"PROCESSING\"}").poll();
        var order = inOrder(service, sqs);
        order.verify(service).handle(any());
        order.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void databaseFailureLeavesMessageForRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(service).handle(any());
        consumer("{}").poll();
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void malformedJsonIsNotAcknowledged() {
        consumer("not json").poll();
        verifyNoInteractions(service);
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
