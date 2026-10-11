package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.access.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Service @RequiredArgsConstructor
public class BillingJobService {
    public record Attempt(Long id, String token, Long paymentId, Store store, String referenceCiphertext,
                          String productId, Action action, long occurredAt, String operationKey, String transactionId) {}
    private final BillingJobRepository jobs;
    private final PaymentRepository payments;
    private final BillingMutation mutation;
    private final AdminPermissionService permissions;
    private final AdminAuditWriter adminAudit;
    private final BillingAuditRepository audits;

    @Transactional(readOnly = true)
    public List<Long> available() { return jobs.available(LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 50)); }
    @Transactional
    public Attempt claim(Long id) {
        var j = jobs.lock(id).orElseThrow(BillingSupport::invalid);
        if (!j.claimable(LocalDateTime.now())) return null;
        if (j.getAttempts() >= 10) { j.failed("LEASE_EXPIRED"); return null; }
        String token = j.claim(LocalDateTime.now()); var p = j.getPayment();
        return new Attempt(id, token, p == null ? null : p.getId(), p == null ? null : p.getStore(),
                p == null ? null : p.getReferenceCiphertext(), p == null ? null : p.getProductId(),
                j.getAction(), j.getOccurredAt(), "event:" + j.getEventKey(), j.getTransactionId());
    }
    @Transactional
    public void finish(Attempt a, StoreGateway.Purchase purchase) {
        var j = jobs.lock(a.id()).orElseThrow();
        if (!j.owns(a.token())) return;
        if (a.action() == Action.CONSUME) payments.lock(a.paymentId()).orElseThrow().consumed();
        else if (a.action() != Action.IGNORE) mutation.apply(a.paymentId(), purchase, null, a.action(),
                a.occurredAt(), a.operationKey(), a.id(), a.transactionId());
        j.complete();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Attempt a, String code) {
        var j = jobs.lock(a.id()).orElseThrow();
        if (j.owns(a.token())) j.failed(code);
    }
    @Transactional
    public void retry(Long actor, Long id, String idempotencyKey) {
        var j = jobs.lock(id).orElseThrow(BillingSupport::invalid);
        permissions.require(actor, AdminPermission.billing_adjust);
        String key = "retry:" + BillingSupport.hash(actor + ":" + idempotencyKey);
        var old = audits.findByOperationKey(key);
        if (old.isPresent()) {
            if (!Objects.equals(old.get().getJobId(), id)) throw BillingSupport.conflict();
            return;
        }
        if (j.getState() == JobState.COMPLETED || j.getState() == JobState.PROCESSING) throw BillingSupport.conflict();
        j.retry();
        audits.saveAndFlush(new BillingAudit(j.getPayment() == null ? null : j.getPayment().getId(),
                null, id, actor, "RETRY", key));
        var ctx = AdminAuditContext.get();
        adminAudit.enlisted(actor, "BILLING_RETRY", "billing-job:" + id, "SUCCESS", "OPERATOR_RETRY",
                ctx == null ? UUID.randomUUID().toString() : ctx.correlationId);
    }
}
