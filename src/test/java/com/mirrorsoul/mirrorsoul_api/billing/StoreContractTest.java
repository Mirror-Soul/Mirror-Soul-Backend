package com.mirrorsoul.mirrorsoul_api.billing;

import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

class StoreContractTest {
    final BillingProperties properties = new BillingProperties();
    final ObjectMapper mapper = new ObjectMapper();
    final OfficialStoreGateway gateway = new OfficialStoreGateway(properties, mapper);
    String google(UUID owner, String state, int remaining) {
        return """
                {"orderId":"order-1","obfuscatedExternalAccountId":"%s",
                 "purchaseStateContext":{"purchaseState":"%s"},
                 "productLineItem":[{"productId":"time","productOfferDetails":{"quantity":3,"refundableQuantity":%d,
                 "consumptionState":"CONSUMPTION_STATE_CONSUMED"}}]}
                """.formatted(owner, state, remaining);
    }
    @Test void googleReadsServerStateQuantityOwnershipAndConsumption() {
        var owner = UUID.randomUUID();
        var p = gateway.decodeGoogle("secret-token", mapper.readTree(google(owner, "PURCHASED", 2)));
        assertThat(p.quantity()).isEqualTo(3); assertThat(p.refundableQuantity()).isEqualTo(2);
        assertThat(p.accountId()).isEqualTo(owner); assertThat(p.consumed()).isTrue();
        assertThat(gateway.decodeGoogle("token", mapper.readTree(google(owner, "PENDING", 3))).purchased()).isFalse();
        assertThat(gateway.decodeGoogle("token", mapper.readTree(google(owner, "CANCELLED", 3))).revoked()).isTrue();
    }
    @Test void testPurchaseMissingOwnershipAndUnknownStateAreRejected() {
        var json = google(UUID.randomUUID(), "PURCHASED", 3);
        var test = json.replace("{\"orderId\"", "{\"testPurchaseContext\":{},\"orderId\"");
        assertThatThrownBy(() -> gateway.decodeGoogle("token", mapper.readTree(test))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> gateway.decodeGoogle("token", mapper.readTree(json.replace("obfuscatedExternalAccountId", "missing"))))
                .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> gateway.decodeGoogle("token", mapper.readTree(google(UUID.randomUUID(), "UNKNOWN", 3))))
                .isInstanceOf(GeneralException.class);
    }
    @Test void authenticatedRtdnUsesFullAndPartialRefundContractsAndPackageBinding() {
        properties.getGoogle().setPackageName("test.package");
        var full = mapper.readTree("""
                {"packageName":"test.package","eventTimeMillis":"1000",
                 "voidedPurchaseNotification":{"purchaseToken":"secret","orderId":"order","productType":2,"refundType":1}}
                """);
        assertThat(gateway.decodeGoogleNotification(full, "event").action()).isEqualTo(Action.REVOKE);
        var partial = mapper.readTree(full.toString().replace("\"refundType\":1", "\"refundType\":2"));
        assertThat(gateway.decodeGoogleNotification(partial, "event").action()).isEqualTo(Action.REVOKE_PARTIAL);
        properties.getGoogle().setPackageName("wrong.package");
        assertThatThrownBy(() -> gateway.decodeGoogleNotification(full, "event")).isInstanceOf(GeneralException.class);
    }
    @Test void credentialsAndWebhookAuthenticationFailClosed() {
        assertThatThrownBy(() -> gateway.verify(Store.APPLE, "123")).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> gateway.appleNotification("forged-jws")).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> gateway.googleNotification(null, "e30=", "1")).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> new BillingCipher(properties).encrypt("token")).isInstanceOf(GeneralException.class);
    }
    @Test void encryptedEvidenceUsesRandomNoncesAndRejectsWrongKeys() {
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var cipher = new BillingCipher(properties); String a = cipher.encrypt("secret");
        assertThat(cipher.encrypt("secret")).isNotEqualTo(a);
        assertThat(a).doesNotContain("secret"); assertThat(cipher.decrypt(a)).isEqualTo("secret");
        byte[] wrong = new byte[32]; Arrays.fill(wrong, (byte) 1);
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(wrong));
        assertThatThrownBy(() -> cipher.decrypt(a)).isInstanceOf(GeneralException.class);
    }
}
