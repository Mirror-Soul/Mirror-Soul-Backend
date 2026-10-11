package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Component @RequiredArgsConstructor @Slf4j
public class BillingWorker {
    private final BillingJobService jobs;
    private final StoreGateway store;
    private final BillingCipher cipher;
    private final MeterRegistry metrics;
    @Scheduled(fixedDelayString = "${billing.retry-interval-ms:30000}")
    public void run() {
        for (Long id : jobs.available()) process(id);
    }
    public void process(Long id) {
        var a = jobs.claim(id); if (a == null) return;
        try {
            StoreGateway.Purchase p = null;
            if (a.action() == Action.CONSUME) store.consume(cipher.decrypt(a.referenceCiphertext()), a.productId());
            else if (a.action() != Action.IGNORE && a.action() != Action.REVOKE)
                p = store.verify(a.store(), cipher.decrypt(a.referenceCiphertext()));
            jobs.finish(a, p);
            metrics.counter("billing.jobs.completed", "action", a.action().name()).increment();
        } catch (RuntimeException e) {
            String code = e instanceof GeneralException g ? g.getCode().getCode() : "PROCESSING_FAILED";
            jobs.failed(a, code);
            metrics.counter("billing.jobs.failed", "action", a.action().name()).increment();
            log.error("Billing job failed jobId={} paymentId={} action={} failureCode={}", id, a.paymentId(), a.action(), code);
        }
    }
}
