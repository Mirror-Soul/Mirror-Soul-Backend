package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.transaction.support.*;

public final class QualitySupport {
    private QualitySupport() {}
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static UUID correlation(String id) { return UUID.nameUUIDFromBytes(("mirrorsoul:" + id).getBytes(StandardCharsets.UTF_8)); }
    public static GeneralException invalid() { return new GeneralException(GeneralErrorCode.QUALITY_INVALID); }
    public static GeneralException conflict() { return new GeneralException(GeneralErrorCode.QUALITY_CONFLICT); }
    public static GeneralException missing() { return new GeneralException(GeneralErrorCode.QUALITY_NOT_FOUND); }
    public static LocalDateTime local(OffsetDateTime at) {
        return LocalDateTime.ofInstant(at.toInstant(), ZoneId.systemDefault()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }
    public static void afterCommit(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("Transaction required");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { work.run(); }
        });
    }
    public enum Failure { DISPATCH_FAILED, QUALITY_GATE_FAILED, INVALID_INPUT, RATE_LIMITED, PROVIDER_UNAVAILABLE,
        TRAINING_TIMEOUT, TRAINING_FAILED, AUTHORIZATION_FAILED }
    public static Failure safeFailure(String code) {
        if (code == null) return null;
        try { return Failure.valueOf(code); } catch (IllegalArgumentException ignored) {}
        if ("GEMINI_HTTP_429".equals(code)) return Failure.RATE_LIMITED;
        if (code.startsWith("GEMINI_HTTP_5")) return Failure.PROVIDER_UNAVAILABLE;
        if ("INVALID_GENERATED_PROFILE".equals(code)) return Failure.QUALITY_GATE_FAILED;
        return Failure.TRAINING_FAILED;
    }
}
