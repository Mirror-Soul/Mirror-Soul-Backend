package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Entity @Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_identity", columnNames = "identity_hash"),
        @UniqueConstraint(name = "uk_payments_transaction", columnNames = {"store", "store_transaction_id"})})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Store store;
    @Column(name = "identity_hash", nullable = false, length = 64) private String identityHash;
    @Column(name = "reference_ciphertext", nullable = false, columnDefinition = "TEXT") private String referenceCiphertext;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @Column(name = "store_transaction_id", length = 200) private String transactionId;
    @Column(name = "product_id", length = 200) private String productId;
    @Column(name = "unit_seconds", nullable = false) private int unitSeconds;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "granted_seconds", nullable = false) private int grantedSeconds;
    @Column(name = "revoked_seconds", nullable = false) private int revokedSeconds;
    @Enumerated(EnumType.STRING) @Column(name = "verification_status", nullable = false, length = 20) private Verification verification = Verification.UNVERIFIED;
    @Enumerated(EnumType.STRING) @Column(name = "grant_status", nullable = false, length = 20) private Grant grant = Grant.NOT_GRANTED;
    @Enumerated(EnumType.STRING) @Column(name = "refund_status", nullable = false, length = 20) private Refund refund = Refund.NONE;
    @Enumerated(EnumType.STRING) @Column(name = "consumption_status", nullable = false, length = 20) private Consumption consumption = Consumption.NOT_REQUIRED;
    @Column(name = "refund_event_at", nullable = false) private long refundEventAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public Payment(Store store, String identityHash, String referenceCiphertext) {
        this.store = store; this.identityHash = identityHash; this.referenceCiphertext = referenceCiphertext;
    }
    void verified(User user, StoreGateway.Purchase purchase, int seconds) {
        if (this.user != null && !this.user.getId().equals(user.getId())) throw BillingSupport.conflict();
        if (transactionId != null && !transactionId.equals(purchase.transactionId())) throw BillingSupport.conflict();
        if (verification == Verification.VERIFIED && (!productId.equals(purchase.productId())
                || quantity != purchase.quantity() || !transactionId.equals(purchase.transactionId()))) throw BillingSupport.conflict();
        this.user = user; this.transactionId = purchase.transactionId(); this.productId = purchase.productId();
        if (verification != Verification.VERIFIED) { unitSeconds = seconds; quantity = purchase.quantity(); }
        verification = Verification.VERIFIED; updatedAt = LocalDateTime.now();
    }
    void granted(int seconds, boolean consumed) {
        grantedSeconds = seconds; grant = Grant.GRANTED;
        consumption = store == Store.GOOGLE && !consumed ? Consumption.PENDING :
                store == Store.GOOGLE ? Consumption.COMPLETED : Consumption.NOT_REQUIRED;
        updatedAt = LocalDateTime.now();
    }
    void refund(int revokedSeconds, Refund status, long occurredAt) {
        this.revokedSeconds = revokedSeconds; this.refund = status;
        this.refundEventAt = Math.max(refundEventAt, occurredAt);
        grant = grantedSeconds == 0 ? Grant.NOT_GRANTED :
                revokedSeconds == grantedSeconds ? Grant.REVOKED : Grant.GRANTED;
        updatedAt = LocalDateTime.now();
    }
    void consumed() { consumption = Consumption.COMPLETED; updatedAt = LocalDateTime.now(); }
    void transactionHint(String value) {
        if (value == null || value.isBlank()) return;
        if (transactionId != null && !transactionId.equals(value)) throw BillingSupport.conflict();
        transactionId = value;
    }
}
