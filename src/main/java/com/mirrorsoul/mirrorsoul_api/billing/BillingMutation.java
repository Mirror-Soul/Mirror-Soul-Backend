package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Service @RequiredArgsConstructor
public class BillingMutation {
    private final PaymentRepository payments;
    private final UserRepository users;
    private final TalkTimeLedger ledger;
    private final BillingProperties properties;
    private final BillingAuditRepository audits;
    private final BillingJobRepository jobs;

    @Transactional
    public void apply(Long id, StoreGateway.Purchase purchase, Long expectedMemberId, Action action,
                      long occurredAt, String operationKey, Long jobId, String eventOrderId) {
        Payment p = payments.lock(id).orElseThrow(BillingSupport::invalid);
        p.transactionHint(eventOrderId);
        if (eventOrderId != null && !eventOrderId.isBlank() && p.getTransactionId() != null
                && !eventOrderId.equals(p.getTransactionId())) throw BillingSupport.conflict();
        if (occurredAt > 0 && occurredAt < p.getRefundEventAt()) return;
        User user = null;
        if (purchase != null) {
            if (purchase.store() != p.getStore()) throw BillingSupport.conflict();
            user = users.findByUuidForUpdate(purchase.accountId()).orElseThrow(BillingSupport::invalid);
            if (expectedMemberId != null && !user.getId().equals(expectedMemberId)) throw BillingSupport.conflict();
            if (!purchase.purchased() && !purchase.revoked()) throw BillingSupport.conflict();
            if (purchase.transactionId() == null) {
                if (purchase.revoked() && p.getVerification() == Verification.UNVERIFIED) {
                    p.refund(0, Refund.CANCELLED, occurredAt); return;
                }
                throw BillingSupport.invalid();
            }
            p.verified(user, purchase, p.getVerification() == Verification.VERIFIED
                    ? p.getUnitSeconds() : properties.seconds(p.getStore(), purchase.productId()));
        } else if (p.getUser() != null) {
            user = users.findByIdForUpdate(p.getUser().getId()).orElseThrow(BillingSupport::invalid);
        }
        if (action == Action.REVOKE) {
            reclaim(p, user, p.getGrantedSeconds(), Refund.REFUNDED, occurredAt, operationKey, jobId);
            return;
        }
        if (purchase == null) throw BillingSupport.invalid();
        if (action == Action.RESTORE) {
            if (purchase.revoked() || !purchase.purchased()) throw BillingSupport.conflict();
            if (p.getRevokedSeconds() > 0) {
                append(p, user, TalkTimeTransactionReason.PAYMENT_RETURN, p.getRevokedSeconds(), operationKey, jobId);
            }
            p.refund(0, Refund.REVERSED, occurredAt);
        }
        boolean blockedByRefund = p.getRefund() == Refund.REFUNDED || p.getRefund() == Refund.CANCELLED;
        if (p.getGrantedSeconds() == 0 && !blockedByRefund && purchase.purchased() && !purchase.revoked()) {
            int seconds;
            try { seconds = Math.multiplyExact(p.getUnitSeconds(), p.getQuantity()); }
            catch (ArithmeticException e) { throw BillingSupport.invalid(); }
            append(p, user, TalkTimeTransactionReason.PURCHASE_GRANT, seconds, "grant:" + p.getId(), jobId);
            p.granted(seconds, purchase.consumed());
            if (p.getConsumption() == Consumption.PENDING) {
                jobs.save(new BillingJob(BillingSupport.hash("consume:" + p.getId()), p, Action.CONSUME, 0));
            }
        }
        if (action != Action.RESTORE) {
            int target = purchase.revoked() ? p.getGrantedSeconds() :
                    purchase.refundPercentage() > 0
                            ? Math.toIntExact((p.getGrantedSeconds() * (long) purchase.refundPercentage() + 99) / 100)
                            : Math.multiplyExact(p.getUnitSeconds(), p.getQuantity() - purchase.refundableQuantity());
            // A late client verification can never resurrect or undo a signed refund reversal.
            if (target > p.getRevokedSeconds() && (occurredAt > 0 || p.getRefund() != Refund.REVERSED
                    || purchase.revocationTime() > p.getRefundEventAt())) {
                reclaim(p, user, target, target == p.getGrantedSeconds() ? Refund.REFUNDED : Refund.PARTIAL,
                        Math.max(occurredAt, purchase.revocationTime()), operationKey, jobId);
            } else if (purchase.revoked() && p.getGrantedSeconds() == 0) p.refund(0, Refund.REFUNDED, occurredAt);
        }
    }
    private void reclaim(Payment p, User user, int target, Refund status, long at, String key, Long jobId) {
        if (target <= p.getRevokedSeconds() && status == p.getRefund() && at <= p.getRefundEventAt()) return;
        int delta = p.getRevokedSeconds() - target;
        String refundKey = "refund:" + BillingSupport.hash(p.getId() + ":" + p.getRefundEventAt() + ":" + key + ":" + target);
        if (delta < 0) append(p, user, TalkTimeTransactionReason.PAYMENT_REFUND, delta, refundKey, jobId);
        p.refund(Math.max(target, p.getRevokedSeconds()), status, at);
        audits.save(new BillingAudit(p.getId(), null, jobId, null, "REFUND_STATE", null));
    }
    private void append(Payment p, User user, TalkTimeTransactionReason reason, int seconds, String key, Long jobId) {
        if (user == null) throw BillingSupport.conflict();
        var entry = ledger.append(user, null, reason, seconds, p.getId(), key, null, reason.name(), null);
        audits.save(new BillingAudit(p.getId(), entry.getId(), jobId, null, reason.name(), null));
    }
}
