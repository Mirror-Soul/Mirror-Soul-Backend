package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class BillingReadService {
    private final PaymentRepository payments;
    private final TalkTimeTransactionRepository entries;
    private final UserRepository users;
    private final BillingJobRepository jobs;
    private final TalkTimeLedger ledger;
    static Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw BillingSupport.invalid();
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
    public PaymentView payment(Long id) { return view(payments.findById(id).orElseThrow(BillingSupport::invalid)); }
    PaymentView view(Payment p) {
        return new PaymentView(p.getId(), p.getStore(), p.getTransactionId(),
                p.getUser() == null ? null : p.getUser().getId(), p.getProductId(), p.getVerification(),
                p.getGrant(), p.getRefund(), p.getConsumption(), p.getGrantedSeconds(), p.getRevokedSeconds(),
                entries.findByPaymentIdOrderByIdAsc(p.getId()).stream().map(TalkTimeTransaction::getId).toList(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
    public Page<PaymentView> payments(Store store, Long memberId, Verification verification, Grant grant,
                                      Refund refund, String transactionId, int page, int size) {
        Specification<Payment> spec = (r, q, cb) -> cb.conjunction();
        if (store != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("store"), store));
        if (memberId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("user").get("id"), memberId));
        if (verification != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("verification"), verification));
        if (grant != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("grant"), grant));
        if (refund != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("refund"), refund));
        if (transactionId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("transactionId"), transactionId));
        return payments.findAll(spec, page(page, size)).map(this::view);
    }
    public LedgerView entry(TalkTimeTransaction e) {
        String transaction = e.getPaymentId() == null ? null : payments.findById(e.getPaymentId()).map(Payment::getTransactionId).orElse(null);
        return new LedgerView(e.getId(), e.getUser().getId(), e.getReason().name(), e.getDeltaSeconds(),
                e.getBalanceAfterSeconds(), e.getPaymentId(), transaction,
                e.getVideoCall() == null ? null : e.getVideoCall().getId(), e.getActorId(), e.getMemo(), e.getCreatedAt());
    }
    public Page<LedgerView> ledger(Long memberId, int page, int size) {
        if (!users.existsById(memberId)) throw BillingSupport.invalid();
        return entries.findByUserIdOrderByIdDesc(memberId, page(page, size)).map(this::entry);
    }
    public Balance balance(Long memberId) {
        int balance = ledger.balance(memberId);
        return new Balance(balance, Math.max(0, balance));
    }
    public Page<JobView> jobs(Long paymentId, int page, int size) {
        if (!payments.existsById(paymentId)) throw BillingSupport.invalid();
        return jobs.findByPaymentIdOrderByIdDesc(paymentId, page(page, size)).map(JobView::from);
    }
}
