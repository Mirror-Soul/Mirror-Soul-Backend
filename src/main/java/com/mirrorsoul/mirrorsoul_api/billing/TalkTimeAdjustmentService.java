package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.access.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;

@Service @RequiredArgsConstructor
public class TalkTimeAdjustmentService {
    private final AdminPermissionService permissions;
    private final UserRepository users;
    private final TalkTimeTransactionRepository entries;
    private final TalkTimeLedger ledger;
    private final BillingReadService reads;
    private final AdminAuditWriter adminAudit;
    private final BillingAuditRepository audits;
    @Transactional
    public LedgerView adjust(Long actor, Adjustment request) {
        if (request.deltaSeconds() == null || request.deltaSeconds() == 0 || request.deltaSeconds() == Integer.MIN_VALUE
                || request.reason() == null || request.reason().isBlank() || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()) throw BillingSupport.invalid();
        String key = "adjust:" + BillingSupport.hash(actor + ":" + request.idempotencyKey());
        String fingerprint = BillingSupport.hash(request.memberId() + ":" + request.deltaSeconds() + ":" + request.reason().strip());
        var user = users.findByIdForUpdate(request.memberId()).orElseThrow(BillingSupport::invalid);
        permissions.require(actor, AdminPermission.billing_adjust);
        var old = entries.findByOperationKey(key);
        if (old.isPresent()) {
            if (!fingerprint.equals(old.get().getRequestHash())) throw BillingSupport.conflict();
            return reads.entry(old.get());
        }
        var e = ledger.append(user, null, TalkTimeTransactionReason.ADMIN_ADJUSTMENT,
                request.deltaSeconds(), null, key, actor, request.reason().strip(), fingerprint);
        audits.save(new BillingAudit(null, e.getId(), null, actor, "ADMIN_ADJUSTMENT", key));
        var ctx = AdminAuditContext.get();
        adminAudit.enlisted(actor, "TALK_TIME_ADJUSTMENT", "member:" + user.getId(), "SUCCESS", "BILLING_ADJUSTMENT",
                ctx == null ? UUID.randomUUID().toString() : ctx.correlationId);
        return reads.entry(e);
    }
}
