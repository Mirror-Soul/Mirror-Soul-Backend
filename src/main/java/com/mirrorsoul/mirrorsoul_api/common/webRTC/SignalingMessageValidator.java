package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import java.util.Map;
import java.util.Set;

final class SignalingMessageValidator {

    private static final String SERVER_SIGNAL_ID = "server";
    private static final String AI_SERVER_SIGNAL_ID = "ai-server";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "JOIN",
            "LEAVE",
            "CALL_INVITE",
            "CALL_ACCEPT",
            "CALL_REJECT",
            "CALL_END",
            "OFFER",
            "ANSWER",
            "ICE"
    );

    void validate(SignalingMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message is required.");
        }

        String type = requireText(message.getType(), "type");
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported signaling type: " + type);
        }

        switch (type) {
            case "JOIN" -> validateJoin(message);
            case "LEAVE" -> validateLeave(message);
            case "CALL_INVITE" -> {
                validateRelayEnvelope(message, Direction.CALLER_TO_AI);
                validateData(message.getData(), Set.of("callId"));
            }
            case "CALL_ACCEPT", "CALL_REJECT", "ANSWER" -> {
                validateRelayEnvelope(message, Direction.AI_TO_CALLER);
                validateTypedData(type, message.getData());
            }
            case "OFFER" -> {
                validateRelayEnvelope(message, Direction.CALLER_TO_AI);
                validateTypedData(type, message.getData());
            }
            case "CALL_END", "ICE" -> {
                validateRelayEnvelope(message, Direction.BIDIRECTIONAL);
                validateTypedData(type, message.getData());
            }
            default -> throw new IllegalArgumentException("Unsupported signaling type: " + type);
        }
    }

    private void validateTypedData(String type, Object rawData) {
        switch (type) {
            case "CALL_ACCEPT", "CALL_END" -> validateData(rawData, Set.of("callId"));
            case "CALL_REJECT" -> {
                Map<?, ?> data = validateData(rawData, Set.of("callId", "reason", "detail"));
                requireText(data.get("reason"), "data.reason");
                requireText(data.get("detail"), "data.detail");
            }
            case "OFFER" -> validateSessionDescription(rawData, "offer");
            case "ANSWER" -> validateSessionDescription(rawData, "answer");
            case "ICE" -> validateIceCandidate(rawData);
            default -> throw new IllegalArgumentException("Unsupported signaling type: " + type);
        }
    }

    private void validateJoin(SignalingMessage message) {
        String from = requireText(message.getFrom(), "from");
        requireExact(message.getTo(), SERVER_SIGNAL_ID, "to");
        requireNull(message.getData(), "data");

        if (AI_SERVER_SIGNAL_ID.equals(from)) {
            if (hasText(message.getRoomId())) {
                throw new IllegalArgumentException("AI server JOIN must not include roomId.");
            }
            return;
        }

        String roomId = requireText(message.getRoomId(), "roomId");
        requireExact(from, callerSignalId(roomId), "from");
    }

    private void validateLeave(SignalingMessage message) {
        String from = requireText(message.getFrom(), "from");
        requireExact(message.getTo(), SERVER_SIGNAL_ID, "to");
        requireNull(message.getData(), "data");

        if (AI_SERVER_SIGNAL_ID.equals(from)) {
            if (hasText(message.getRoomId())) {
                throw new IllegalArgumentException("AI server LEAVE must not include roomId.");
            }
            return;
        }

        String roomId = requireText(message.getRoomId(), "roomId");
        requireExact(from, callerSignalId(roomId), "from");
    }

    private void validateRelayEnvelope(SignalingMessage message, Direction direction) {
        String roomId = requireText(message.getRoomId(), "roomId");
        String from = requireText(message.getFrom(), "from");
        String to = requireText(message.getTo(), "to");
        String callerSignalId = callerSignalId(roomId);
        String aiSignalId = aiSignalId(roomId);

        boolean callerToAi = callerSignalId.equals(from) && aiSignalId.equals(to);
        boolean aiToCaller = aiSignalId.equals(from) && callerSignalId.equals(to);

        boolean validDirection = switch (direction) {
            case CALLER_TO_AI -> callerToAi;
            case AI_TO_CALLER -> aiToCaller;
            case BIDIRECTIONAL -> callerToAi || aiToCaller;
        };

        if (!validDirection) {
            throw new IllegalArgumentException(
                    "from/to do not match roomId or message direction."
            );
        }
    }

    private Map<?, ?> validateData(Object rawData, Set<String> allowedKeys) {
        if (!(rawData instanceof Map<?, ?> data)) {
            throw new IllegalArgumentException("data must be an object.");
        }
        if (!data.keySet().equals(allowedKeys)) {
            throw new IllegalArgumentException(
                    "data must contain exactly these fields: " + allowedKeys
            );
        }
        requirePositiveInteger(data.get("callId"), "data.callId");
        return data;
    }

    private void validateSessionDescription(Object rawData, String expectedType) {
        Map<?, ?> data = validateData(rawData, Set.of("callId", "sdp"));
        if (!(data.get("sdp") instanceof Map<?, ?> sdp)) {
            throw new IllegalArgumentException("data.sdp must be an object.");
        }
        if (!sdp.keySet().equals(Set.of("type", "sdp"))) {
            throw new IllegalArgumentException("data.sdp must contain type and sdp only.");
        }
        requireExact(sdp.get("type"), expectedType, "data.sdp.type");
        requireText(sdp.get("sdp"), "data.sdp.sdp");
    }

    private void validateIceCandidate(Object rawData) {
        Map<?, ?> data = validateData(rawData, Set.of("callId", "candidate"));
        if (!(data.get("candidate") instanceof Map<?, ?> candidate)) {
            throw new IllegalArgumentException("data.candidate must be an object.");
        }

        Set<?> keys = candidate.keySet();
        if (!keys.contains("candidate")
                || !keys.stream().allMatch(Set.of("candidate", "sdpMid", "sdpMLineIndex")::contains)) {
            throw new IllegalArgumentException(
                    "data.candidate supports candidate, sdpMid and sdpMLineIndex only."
            );
        }

        requireText(candidate.get("candidate"), "data.candidate.candidate");
        Object sdpMid = candidate.get("sdpMid");
        if (sdpMid != null && !(sdpMid instanceof String)) {
            throw new IllegalArgumentException("data.candidate.sdpMid must be a string or null.");
        }
        Object lineIndex = candidate.get("sdpMLineIndex");
        if (lineIndex != null) {
            requireNonNegativeInteger(lineIndex, "data.candidate.sdpMLineIndex");
        }
    }

    private void requirePositiveInteger(Object value, String field) {
        if (!isIntegralNumber(value) || ((Number) value).longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer.");
        }
    }

    private void requireNonNegativeInteger(Object value, String field) {
        if (!isIntegralNumber(value) || ((Number) value).longValue() < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative integer.");
        }
    }

    private boolean isIntegralNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof java.math.BigInteger;
    }

    private String requireText(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string.");
        }
        return text;
    }

    private void requireExact(Object actual, String expected, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(field + " must be " + expected + ".");
        }
    }

    private void requireNull(Object value, String field) {
        if (value != null) {
            throw new IllegalArgumentException(field + " must be null.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String callerSignalId(String roomId) {
        return "signal:" + roomId + ":caller";
    }

    private String aiSignalId(String roomId) {
        return "signal:" + roomId + ":ai";
    }

    private enum Direction {
        CALLER_TO_AI,
        AI_TO_CALLER,
        BIDIRECTIONAL
    }
}
