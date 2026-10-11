package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class TalkTimeLedger {
    private final TalkTimeTransactionRepository entries;
    @Transactional(readOnly = true)
    public int balance(Long userId) { return Math.toIntExact(entries.balance(userId)); }
    // The caller holds the user's row lock; this also serializes unrelated payments and calls.
    @Transactional(propagation = Propagation.MANDATORY)
    public TalkTimeTransaction append(User user, VideoCall call, TalkTimeTransactionReason reason,
            int delta, Long paymentId, String operationKey, Long actorId, String memo, String requestHash) {
        if (operationKey != null) {
            var existing = entries.findByOperationKey(operationKey);
            if (existing.isPresent()) return existing.get();
        }
        int after;
        try { after = Math.addExact(balance(user.getId()), delta); }
        catch (ArithmeticException e) { throw BillingSupport.conflict(); }
        user.updateTalkTimeBalance(after);
        return entries.saveAndFlush(TalkTimeTransaction.ledger(user, call, reason, delta,
                paymentId, operationKey, actorId, memo, requestHash));
    }
}
