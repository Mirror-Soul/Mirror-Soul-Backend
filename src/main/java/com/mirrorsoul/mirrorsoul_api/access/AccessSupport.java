package com.mirrorsoul.mirrorsoul_api.access;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
final class AccessSupport {
 private AccessSupport() {}
 static LocalDateTime now() {return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);}
 static String hash(Object... values) {try {var d=MessageDigest.getInstance("SHA-256");for(var v:values) {var b=String.valueOf(v).getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}}
 static String token() {byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static void denied() {throw new GeneralException(GeneralErrorCode.FORBIDDEN);}
 static void conflict() {throw new GeneralException(GeneralErrorCode.STAFF_CONFLICT);}
}
