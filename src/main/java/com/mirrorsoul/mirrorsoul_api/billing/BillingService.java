package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;

@Service @RequiredArgsConstructor
public class BillingService {
    private final StoreGateway store;
    private final BillingRegistry registry;
    private final BillingMutation mutation;
    private final BillingReadService reads;
    private final BillingWorker worker;
    private final UserRepository users;
    public PaymentView verify(Long memberId, Verify request) {
        var member = users.findById(memberId).orElseThrow(BillingSupport::invalid);
        if (member.getStatus() != UserStatus.ACTIVE) throw BillingSupport.invalid();
        var p = store.verify(request.store(), request.reference());
        if (!member.getUuid().equals(p.accountId())) throw BillingSupport.conflict();
        if (!p.purchased() && !p.revoked()) throw BillingSupport.conflict();
        Long id = reserve(request.store(), request.reference());
        try { mutation.apply(id, p, memberId, Action.SYNC, 0, "verify:" + id, null, null); }
        catch (DataIntegrityViolationException e) { throw BillingSupport.conflict(); }
        return reads.payment(id);
    }
    private Long reserve(Store store, String reference) {
        // Each attempt has its own transaction so unique-key races do not poison the caller's transaction.
        for (int i = 0; i < 3; i++) {
            try { return registry.reserve(store, reference); }
            catch (DataIntegrityViolationException e) { if (i == 2) throw BillingSupport.conflict(); }
        }
        throw BillingSupport.conflict();
    }
    public void notification(StoreGateway.Notification n) {
        Long paymentId = n.action() == Action.IGNORE ? null : reserve(n.store(), n.reference());
        Long id = null;
        for (int i = 0; i < 3; i++) {
            try { id = registry.notification(n, paymentId); break; }
            catch (DataIntegrityViolationException e) { if (i == 2) throw BillingSupport.conflict(); }
        }
        // Receipt is durable before any external API is called. Failures remain in the retry queue.
        worker.process(id);
    }
}
