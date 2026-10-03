package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignalingHandler extends TextWebSocketHandler {

    private static final String AI_SERVER_SIGNAL_ID = "ai-server";

    private final SignalingMessageValidator validator = new SignalingMessageValidator();

    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Signaling WebSocket connected. sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SignalingMessage signalingMessage;
        try {
            signalingMessage = objectMapper.readValue(message.getPayload(), SignalingMessage.class);
            validator.validate(signalingMessage);
        } catch (Exception exception) {
            log.warn(
                    "Invalid signaling message. sessionId={}, detail={}",
                    session.getId(),
                    exception.getMessage()
            );
            sendProtocolError(session, exception.getMessage());
            return;
        }

        switch (signalingMessage.getType()) {
            case "JOIN" -> handleJoin(session, signalingMessage);
            case "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END",
                 "OFFER", "ANSWER", "ICE" -> relayMessage(session, signalingMessage);
            case "LEAVE" -> handleLeave(session, signalingMessage);
            default -> throw new IllegalArgumentException("Unknown signaling type: " + signalingMessage.getType());
        }
    }

    private void handleJoin(WebSocketSession session, SignalingMessage message) throws IOException {
        sessionRegistry.register(message.getFrom(), session);

        SignalingMessage joinedMessage = new SignalingMessage(
                "JOINED",
                message.getRoomId(),
                "server",
                message.getFrom(),
                "joined"
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(joinedMessage)));

        log.info(
                "Signaling participant joined. signalId={}, sessionId={}",
                message.getFrom(),
                session.getId()
        );
    }

    private void relayMessage(WebSocketSession senderSession, SignalingMessage message) throws IOException {
        if (!isAuthorizedSender(senderSession, message.getFrom())) {
            log.warn(
                    "Signaling sender identity mismatch. sessionId={}, claimedFrom={}",
                    senderSession.getId(),
                    message.getFrom()
            );
            sendProtocolError(senderSession, "from is not registered to this WebSocket session.");
            return;
        }

        WebSocketSession receiverSession = getReceiverSession(message.getTo());

        if (receiverSession == null || !receiverSession.isOpen()) {
            log.warn(
                    "Signaling receiver unavailable. type={}, roomId={}, from={}, to={}",
                    message.getType(),
                    message.getRoomId(),
                    message.getFrom(),
                    message.getTo()
            );
            sendDeliveryFailure(senderSession, message);
            return;
        }

        String payload = objectMapper.writeValueAsString(message);
        receiverSession.sendMessage(new TextMessage(payload));
    }

    private void sendDeliveryFailure(WebSocketSession senderSession, SignalingMessage original)
            throws IOException {
        if (!senderSession.isOpen()) {
            log.warn(
                    "Cannot report signaling delivery failure because sender session is closed. "
                            + "type={}, roomId={}, from={}, to={}, sessionId={}",
                    original.getType(),
                    original.getRoomId(),
                    original.getFrom(),
                    original.getTo(),
                    senderSession.getId()
            );
            return;
        }

        boolean callInvite = "CALL_INVITE".equals(original.getType());
        Map<String, Object> data = new LinkedHashMap<>();
        Object callId = extractCallId(original.getData());
        if (callId != null) {
            data.put("callId", callId);
        }
        data.put("reason", callInvite ? "AI_SERVER_UNAVAILABLE" : "RECEIVER_UNAVAILABLE");
        data.put(
                "detail",
                callInvite
                        ? "AI 통화 서버에 연결할 수 없습니다."
                        : "시그널링 수신자에게 메시지를 전달할 수 없습니다."
        );

        SignalingMessage failure = new SignalingMessage(
                callInvite ? "CALL_REJECT" : "SIGNALING_ERROR",
                original.getRoomId(),
                "server",
                original.getFrom(),
                data
        );
        senderSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(failure)));
    }

    private Object extractCallId(Object data) {
        if (data instanceof Map<?, ?> dataMap) {
            return dataMap.get("callId");
        }
        return null;
    }

    private boolean isAuthorizedSender(WebSocketSession session, String signalId) {
        if (sessionRegistry.isRegisteredAs(signalId, session)) {
            return true;
        }
        return isAiSignalId(signalId)
                && sessionRegistry.isRegisteredAs(AI_SERVER_SIGNAL_ID, session);
    }

    private void sendProtocolError(WebSocketSession session, String detail) throws IOException {
        if (!session.isOpen()) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", "INVALID_MESSAGE");
        data.put("detail", detail == null || detail.isBlank()
                ? "시그널링 메시지 형식이 올바르지 않습니다."
                : detail);

        SignalingMessage error = new SignalingMessage(
                "SIGNALING_ERROR",
                null,
                "server",
                null,
                data
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }

    private WebSocketSession getReceiverSession(String signalId) {
        WebSocketSession receiverSession = sessionRegistry.getSession(signalId);

        if ((receiverSession == null || !receiverSession.isOpen()) && isAiSignalId(signalId)) {
            return sessionRegistry.getSession(AI_SERVER_SIGNAL_ID);
        }

        return receiverSession;
    }

    private boolean isAiSignalId(String signalId) {
        return signalId != null && signalId.startsWith("signal:") && signalId.endsWith(":ai");
    }

    private void handleLeave(WebSocketSession session, SignalingMessage message) throws IOException {
        if (!isAuthorizedSender(session, message.getFrom())) {
            sendProtocolError(session, "from is not registered to this WebSocket session.");
            return;
        }
        sessionRegistry.remove(message.getFrom());

        log.info("Signaling participant left. signalId={}", message.getFrom());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        log.info(
                "Signaling WebSocket closed. sessionId={}, statusCode={}, reason={}",
                session.getId(),
                status.getCode(),
                status.getReason()
        );
    }
}
