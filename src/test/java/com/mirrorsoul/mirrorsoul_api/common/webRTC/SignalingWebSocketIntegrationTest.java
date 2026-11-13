package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:signaling;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "management.cloudwatch.metrics.export.enabled=false",
        "monitoring.cloudwatch.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SignalingWebSocketIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private S3Client s3;
    @MockitoBean private SqsClient sqs;

    @Test
    void existingClientsCanConnectJoinAndExchangeCallSignalsWithoutAuthHeaders() throws Exception {
        var aiMessages = new ReceivedMessages();
        var callerMessages = new ReceivedMessages();
        var callerHeaders = new WebSocketHttpHeaders();
        callerHeaders.setOrigin("https://app.example.test");

        try (var ai = connect(aiMessages, new WebSocketHttpHeaders());
             var caller = connect(callerMessages, callerHeaders)) {
            ai.sendMessage(new TextMessage("""
                    {"type":"JOIN","from":"ai-server","to":"server"}
                    """));
            assertThat(nextMessage(aiMessages).get("type").asString()).isEqualTo("JOINED");

            caller.sendMessage(new TextMessage("""
                    {"type":"JOIN","roomId":"call-abc","from":"signal:call-abc:caller","to":"server"}
                    """));
            assertThat(nextMessage(callerMessages).get("type").asString()).isEqualTo("JOINED");

            caller.sendMessage(new TextMessage("""
                    {"type":"CALL_INVITE","roomId":"call-abc","from":"signal:call-abc:caller",
                     "to":"signal:call-abc:ai","data":{"callId":42}}
                    """));
            assertThat(nextMessage(aiMessages).get("type").asString()).isEqualTo("CALL_INVITE");

            ai.sendMessage(new TextMessage("""
                    {"type":"CALL_ACCEPT","roomId":"call-abc","from":"signal:call-abc:ai",
                     "to":"signal:call-abc:caller","data":{"callId":42}}
                    """));
            assertThat(nextMessage(callerMessages).get("type").asString()).isEqualTo("CALL_ACCEPT");
        }
    }

    private WebSocketSession connect(ReceivedMessages handler, WebSocketHttpHeaders headers) throws Exception {
        var uri = URI.create("ws://localhost:" + port + "/ws/signaling");
        return new StandardWebSocketClient().execute(handler, headers, uri).get(5, TimeUnit.SECONDS);
    }

    private JsonNode nextMessage(ReceivedMessages handler) throws InterruptedException {
        String message = handler.messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("WebSocket response").isNotNull();
        return objectMapper.readTree(message);
    }

    private static class ReceivedMessages extends TextWebSocketHandler {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }
    }
}
