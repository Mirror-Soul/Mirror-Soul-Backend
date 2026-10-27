package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class BillingSupport {
    private BillingSupport() {}
    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static GeneralException invalid() { return new GeneralException(GeneralErrorCode.PAYMENT_INVALID); }
    static GeneralException conflict() { return new GeneralException(GeneralErrorCode.PAYMENT_CONFLICT); }
    static GeneralException unavailable() { return new GeneralException(GeneralErrorCode.PAYMENT_UNAVAILABLE); }
}
