package com.mirrorsoul.mirrorsoul_api.billing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class BillingCipher {
    private final BillingProperties properties;
    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getEncryptionKey());
            if (bytes.length != 32) throw BillingSupport.unavailable();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) { throw BillingSupport.unavailable(); }
    }
    public String encrypt(String value) {
        try {
            var secret = key();
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(128, nonce));
            cipher.updateAAD("mirrorsoul-billing-v1".getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (java.security.GeneralSecurityException e) { throw BillingSupport.unavailable(); }
    }
    public String decrypt(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length < 28) throw BillingSupport.unavailable();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12));
            cipher.updateAAD("mirrorsoul-billing-v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) { throw BillingSupport.unavailable(); }
    }
}
