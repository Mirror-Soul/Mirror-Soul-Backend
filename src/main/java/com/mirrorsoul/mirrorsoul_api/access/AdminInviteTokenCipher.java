package com.mirrorsoul.mirrorsoul_api.access;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.util.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
@Component
public class AdminInviteTokenCipher {
 private final String key;
 public AdminInviteTokenCipher(@Value("${security.admin.invite-encryption-key:}") String key) {this.key=key;}
 private SecretKeySpec secret() {try {byte[] bytes=Base64.getDecoder().decode(key);if(bytes.length!=32) throw new IllegalArgumentException();return new SecretKeySpec(bytes,"AES");}catch(IllegalArgumentException e) {throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.SERVICE_UNAVAILABLE,"직원 초대 암호화 키가 설정되지 않았습니다.");}}
 public String encrypt(String token) {
  try {var secret=secret();byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret,new GCMParameterSpec(128,iv));byte[] data=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));var result=java.nio.ByteBuffer.allocate(iv.length+data.length).put(iv).put(data).array();return Base64.getEncoder().encodeToString(result);}catch(java.security.GeneralSecurityException e) {throw new IllegalStateException("Invite encryption failed");}
 }
 public String decrypt(String encrypted) {
  try {byte[] data=Base64.getDecoder().decode(encrypted);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Arrays.copyOf(data,12)));return new String(cipher.doFinal(Arrays.copyOfRange(data,12,data.length)),StandardCharsets.UTF_8);}catch(java.security.GeneralSecurityException|IllegalArgumentException e) {throw new IllegalStateException("Invite decryption failed");}
 }
}
