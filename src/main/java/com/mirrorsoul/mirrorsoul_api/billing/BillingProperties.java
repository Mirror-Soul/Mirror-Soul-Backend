package com.mirrorsoul.mirrorsoul_api.billing;

import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("billing")
@Getter @Setter
public class BillingProperties {
    private String encryptionKey = "";
    private Map<String, Integer> appleProducts = new HashMap<>();
    private Map<String, Integer> googleProducts = new HashMap<>();
    private Apple apple = new Apple();
    private Google google = new Google();
    @Getter @Setter public static class Apple {
        private String productId = "";
        private int productSeconds;
        private String bundleId = "";
        private Long appAppleId;
        private String issuerId = "";
        private String keyId = "";
        private String privateKeyPath = "";
        private List<String> rootCertificatePaths = new ArrayList<>();
        private String environment = "PRODUCTION";
    }
    @Getter @Setter public static class Google {
        private String productId = "";
        private int productSeconds;
        private String packageName = "";
        private String credentialsPath = "";
        private String pushAudience = "";
        private String pushServiceAccount = "";
        private boolean allowTestPurchases = false;
    }
    public int seconds(BillingTypes.Store store, String product) {
        if (product == null || product.isBlank()) throw BillingSupport.invalid();
        Integer seconds = (store == BillingTypes.Store.APPLE ? appleProducts : googleProducts).get(product);
        if (seconds == null) {
            if (store == BillingTypes.Store.APPLE && product.equals(apple.getProductId())) seconds = apple.getProductSeconds();
            if (store == BillingTypes.Store.GOOGLE && product.equals(google.getProductId())) seconds = google.getProductSeconds();
        }
        if (seconds == null || seconds <= 0) throw BillingSupport.invalid();
        return seconds;
    }
    // Credentials must never be included in logs.
    @Override public String toString() { return "BillingProperties[REDACTED]"; }
}
