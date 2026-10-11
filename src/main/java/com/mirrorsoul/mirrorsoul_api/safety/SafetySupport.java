package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
final class SafetySupport {
    private SafetySupport() {}
    static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS); }
    static String key(String key) { return key.toLowerCase(Locale.ROOT); }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static void reader(User user) {
        if(user.getStatus()!=UserStatus.ACTIVE || !Set.of(UserRole.ADMIN,UserRole.ADMIN_VIEWER,UserRole.SUPER_ADMIN).contains(user.getRole()))
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
    }
    static void operator(User user) {
        reader(user);
        if(user.getRole()==UserRole.ADMIN_VIEWER) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
    }
    static void version(Long actual,Long expected) {
        if(!Objects.equals(actual,expected)) throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT,"Version mismatch.");
    }
}
