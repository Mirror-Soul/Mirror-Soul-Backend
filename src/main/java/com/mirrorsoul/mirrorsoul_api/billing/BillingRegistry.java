package com.mirrorsoul.mirrorsoul_api.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Service @RequiredArgsConstructor
public class BillingRegistry {
    private final PaymentRepository payments;
    private final BillingJobRepository jobs;
    private final BillingCipher cipher;
    private final BillingProperties properties;
    String identity(Store store, String reference) {
        String scope = store == Store.APPLE ? properties.getApple().getBundleId() + ":" + properties.getApple().getEnvironment()
                : properties.getGoogle().getPackageName();
        return BillingSupport.hash(store + ":" + scope + ":" + reference);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long reserve(Store store, String reference) {
        String hash = identity(store, reference);
        return payments.findByIdentityHash(hash).map(Payment::getId).orElseGet(() ->
                payments.saveAndFlush(new Payment(store, hash, cipher.encrypt(reference))).getId());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long notification(StoreGateway.Notification notification, Long paymentId) {
        String key = BillingSupport.hash(notification.store() + ":notification:" + notification.eventId());
        var old = jobs.findByEventKey(key);
        if (old.isPresent()) {
            var job = old.get();
            if (job.getAction() != notification.action() || job.getOccurredAt() != notification.occurredAt()
                    || !java.util.Objects.equals(job.getPayment() == null ? null : job.getPayment().getId(), paymentId)
                    || !java.util.Objects.equals(job.getTransactionId(), notification.transactionId())) throw BillingSupport.conflict();
            return job.getId();
        }
        var job = new BillingJob(key, paymentId == null ? null : payments.getReferenceById(paymentId),
                notification.action(), notification.occurredAt());
        job.transaction(notification.transactionId());
        return jobs.saveAndFlush(job).getId();
    }
}
