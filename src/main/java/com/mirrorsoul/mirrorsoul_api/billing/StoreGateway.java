package com.mirrorsoul.mirrorsoul_api.billing;

import java.util.UUID;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

public interface StoreGateway {
    record Purchase(Store store, String reference, String transactionId, String productId,
                    UUID accountId, int quantity, int refundableQuantity, boolean purchased,
                    boolean revoked, boolean consumed, int refundPercentage, long revocationTime) {
        public Purchase(Store store, String reference, String transactionId, String productId, UUID accountId,
                        int quantity, int refundableQuantity, boolean purchased, boolean revoked, boolean consumed) {
            this(store, reference, transactionId, productId, accountId, quantity, refundableQuantity,
                    purchased, revoked, consumed, 0, 0);
        }
    }
    record Notification(Store store, String eventId, String reference, String transactionId,
                        Action action, long occurredAt) {}
    Purchase verify(Store store, String reference);
    Notification appleNotification(String signedPayload);
    Notification googleNotification(String authorization, String data, String messageId);
    void consume(String reference, String productId);
}
