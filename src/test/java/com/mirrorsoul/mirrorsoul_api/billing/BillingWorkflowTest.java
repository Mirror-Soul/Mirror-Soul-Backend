package com.mirrorsoul.mirrorsoul_api.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.access.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingDtos.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({BillingWorkflowTest.Config.class, BillingProperties.class, BillingCipher.class, BillingRegistry.class,
        BillingMutation.class, BillingJobService.class, BillingReadService.class, BillingService.class,
        BillingWorker.class, TalkTimeLedger.class, TalkTimeAdjustmentService.class,
        AdminPermissionService.class, AdminAuditWriter.class, com.mirrorsoul.mirrorsoul_api.service.CallService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingWorkflowTest {
    @TestConfiguration static class Config {
        @Bean SimpleMeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
    @Autowired BillingProperties properties;
    @Autowired BillingService service;
    @Autowired BillingReadService reads;
    @Autowired BillingJobService jobService;
    @Autowired BillingWorker worker;
    @Autowired BillingJobRepository jobs;
    @Autowired TalkTimeAdjustmentService adjustments;
    @Autowired UserRepository users;
    @Autowired TalkTimeLedger ledger;
    @Autowired TalkTimeTransactionRepository entries;
    @Autowired PaymentRepository payments;
    @Autowired BillingRegistry registry;
    @Autowired AdminAuditEventRepository adminAudits;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired com.mirrorsoul.mirrorsoul_api.service.CallService calls;
    @Autowired VideoCallRepository videoCalls;
    @Autowired CloneRepository clones;
    @MockitoBean StoreGateway store;

    @BeforeEach void configure() {
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.getAppleProducts().put("time.apple", 600);
        properties.getGoogleProducts().put("time.google", 60);
        properties.getApple().setBundleId("test.bundle");
        properties.getGoogle().setPackageName("test.package");
    }
    User user(UserRole role, int seconds) {
        var u = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).remainingTalkTime(seconds).createdAt(LocalDateTime.now()).build());
        mutate(() -> ledger.append(users.findByIdForUpdate(u.getId()).orElseThrow(), null,
                TalkTimeTransactionReason.OPENING_BALANCE, seconds, null, "opening:" + u.getId(), null, null, null));
        return u;
    }
    void mutate(Runnable r) { new TransactionTemplate(transactionManager).executeWithoutResult(s -> r.run()); }
    StoreGateway.Purchase purchase(User user, Store type, String ref, int quantity, int remaining) {
        return new StoreGateway.Purchase(type, ref, "order-" + ref,
                type == Store.APPLE ? "time.apple" : "time.google", user.getUuid(), quantity, remaining, true, remaining == 0, false);
    }
    Verify stub(User u, Store type, String ref, int quantity, int remaining) {
        doReturn(purchase(u, type, ref, quantity, remaining)).when(store).verify(type, ref);
        return new Verify(type, ref);
    }
    StoreGateway.Notification notification(Store type, String ref, Action action, long at) {
        return new StoreGateway.Notification(type, UUID.randomUUID().toString(), ref, null, action, at);
    }
    VideoCall call(User u) {
        var clone = clones.findByUserUuid(u.getUuid()).orElseGet(() ->
                clones.saveAndFlush(Clone.builder().user(u).createdAt(LocalDateTime.now()).build()));
        var call = VideoCall.builder().user(u).clone(clone).roomId("call-" + UUID.randomUUID())
                .mediaType(CallMediaType.VOICE).build();
        org.springframework.test.util.ReflectionTestUtils.setField(call, "startedAt", LocalDateTime.now().minusMinutes(10));
        return videoCalls.saveAndFlush(call);
    }
    CallBillingDtos.Event event(String id, CallBillingDtos.Kind kind, LocalDateTime at, CallBillingDtos.EndReason reason) {
        return new CallBillingDtos.Event(id, kind, at.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), reason);
    }
    @Test void waitingIsFreeAndAiAndMemberEndReplaysShareOneDebit() {
        var u = user(UserRole.USER, 100); var call = call(u); var at = LocalDateTime.now().minusSeconds(20);
        var start = event("start", CallBillingDtos.Kind.STARTED, at, null);
        calls.billingEvent(call.getId(), start);
        calls.billingEvent(call.getId(), event("another-start", CallBillingDtos.Kind.STARTED, at.plusSeconds(5), null));
        var end = event("end", CallBillingDtos.Kind.ENDED, at.plusSeconds(10), CallBillingDtos.EndReason.AI_HANGUP);
        calls.billingEvent(call.getId(), end); calls.billingEvent(call.getId(), end);
        var result = calls.endCall(call.getId(), null, u.getUuid());
        assertThat(result.durationSec()).isEqualTo(10);
        assertThat(ledger.balance(u.getId())).isEqualTo(90);
        assertThat(entries.findByVideoCallId(call.getId()).orElseThrow().getDeltaSeconds()).isEqualTo(-10);
        assertThatThrownBy(() -> calls.billingEvent(call.getId(),
                event("end", CallBillingDtos.Kind.ENDED, at.plusSeconds(11), CallBillingDtos.EndReason.AI_HANGUP)))
                .isInstanceOf(GeneralException.class);
    }
    @Test void outOfOrderEndMustRetryAfterStartAndUnconnectedMemberCancellationIsFree() {
        var u = user(UserRole.USER, 100); var call = call(u); var at = LocalDateTime.now().minusSeconds(20);
        var end = event("end", CallBillingDtos.Kind.ENDED, at.plusSeconds(10), CallBillingDtos.EndReason.NETWORK_ERROR);
        assertThatThrownBy(() -> calls.billingEvent(call.getId(), end)).isInstanceOf(GeneralException.class);
        assertThat(ledger.balance(u.getId())).isEqualTo(100);
        calls.billingEvent(call.getId(), event("start", CallBillingDtos.Kind.STARTED, at, null));
        calls.billingEvent(call.getId(), end);
        assertThat(ledger.balance(u.getId())).isEqualTo(90);
        var cancelled = call(u);
        var result = calls.endCall(cancelled.getId(), null, u.getUuid());
        assertThat(result.durationSec()).isZero(); assertThat(result.status()).isEqualTo(VideoCallStatus.CANCELLED);
        assertThat(ledger.balance(u.getId())).isEqualTo(90);
    }
    @Test void refundDuringActiveCallDoesNotEraseAlreadyConsumedTime() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        service.verify(u.getId(), stub(u, Store.APPLE, ref, 1, 1));
        var call = call(u); var at = LocalDateTime.now().minusSeconds(100);
        calls.billingEvent(call.getId(), event("start", CallBillingDtos.Kind.STARTED, at, null));
        service.notification(notification(Store.APPLE, ref, Action.REVOKE, 1000));
        assertThat(ledger.balance(u.getId())).isZero();
        calls.billingEvent(call.getId(), event("end", CallBillingDtos.Kind.ENDED, at.plusSeconds(90), CallBillingDtos.EndReason.USER_HANGUP));
        assertThat(ledger.balance(u.getId())).isEqualTo(-90);
        assertThat(users.findById(u.getId()).orElseThrow().getRemainingTalkTime()).isEqualTo(-90);
    }
    @Test void missingEndCallbackExpiresAtFundedDeadlineAndCannotDebitTwice() {
        var u = user(UserRole.USER, 100); var call = call(u); var at = LocalDateTime.now().minusSeconds(200);
        calls.billingEvent(call.getId(), event("start", CallBillingDtos.Kind.STARTED, at, null));
        calls.endExpired(call.getId()); calls.endExpired(call.getId());
        var ended = videoCalls.findById(call.getId()).orElseThrow();
        assertThat(ended.getDurationSec()).isEqualTo(100);
        assertThat(ended.getBillingEndReason()).isEqualTo("TIME_EXHAUSTED");
        assertThat(ledger.balance(u.getId())).isZero();
        calls.billingEvent(call.getId(), event("late-end", CallBillingDtos.Kind.ENDED,
                LocalDateTime.now(), CallBillingDtos.EndReason.TIME_EXHAUSTED));
        assertThat(ledger.balance(u.getId())).isZero();
    }
    @Test void concurrentAiAndMemberTerminationIsSettledExactlyOnce() throws Exception {
        var u = user(UserRole.USER, 100); var call = call(u); var at = LocalDateTime.now().minusSeconds(20);
        calls.billingEvent(call.getId(), event("start", CallBillingDtos.Kind.STARTED, at, null));
        var end = event("end", CallBillingDtos.Kind.ENDED, at.plusSeconds(10), CallBillingDtos.EndReason.AI_HANGUP);
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        try {
            var ai = pool.submit(() -> { gate.await(); return calls.billingEvent(call.getId(), end); });
            var member = pool.submit(() -> { gate.await(); return calls.endCall(call.getId(), null, u.getUuid()); });
            gate.countDown(); ai.get(30, TimeUnit.SECONDS); member.get(30, TimeUnit.SECONDS);
            var entry = entries.findByVideoCallId(call.getId()).orElseThrow();
            assertThat(entry.getDeltaSeconds()).isBetween(-25, -10);
            assertThat(ledger.balance(u.getId())).isEqualTo(100 + entry.getDeltaSeconds());
        } finally { pool.shutdownNow(); }
    }
    @Test void verifiedPurchaseIsGrantedOnceAndLinkedToLedger() {
        var u = user(UserRole.USER, 1800); String ref = UUID.randomUUID().toString();
        var req = stub(u, Store.APPLE, ref, 1, 1);
        var p = service.verify(u.getId(), req);
        assertThat(service.verify(u.getId(), req).id()).isEqualTo(p.id());
        assertThat(ledger.balance(u.getId())).isEqualTo(2400);
        assertThat(p.verificationStatus()).isEqualTo(Verification.VERIFIED);
        assertThat(p.ledgerIds()).hasSize(1);
        assertThat(reads.ledger(u.getId(), 0, 20).getContent().get(0).transactionId()).isEqualTo("order-" + ref);
        assertThat(users.findById(u.getId()).orElseThrow().getRemainingTalkTime()).isEqualTo(2400);
    }
    @Test void refundAfterSpendingCreatesDebtAndCannotBeRegrantedByReceiptReplay() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        var req = stub(u, Store.APPLE, ref, 1, 1); var p = service.verify(u.getId(), req);
        mutate(() -> ledger.append(users.findByIdForUpdate(u.getId()).orElseThrow(), null,
                TalkTimeTransactionReason.CALL_USAGE, -500, null, "usage:" + u.getId(), null, null, null));
        var n = notification(Store.APPLE, ref, Action.REVOKE, 1000);
        service.notification(n); service.notification(n);
        assertThat(ledger.balance(u.getId())).isEqualTo(-500);
        assertThat(reads.balance(u.getId()).availableSeconds()).isZero();
        assertThat(service.verify(u.getId(), req).refundStatus()).isEqualTo(Refund.REFUNDED);
        assertThat(ledger.balance(u.getId())).isEqualTo(-500);
        assertThat(reads.payment(p.id()).ledgerIds()).hasSize(2);
        String next = UUID.randomUUID().toString();
        service.verify(u.getId(), stub(u, Store.APPLE, next, 1, 1));
        assertThat(ledger.balance(u.getId())).isEqualTo(100);
    }
    @Test void refundBeforeClientVerificationLeavesTombstoneAndNeverGrants() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        service.notification(notification(Store.APPLE, ref, Action.REVOKE, 1000));
        var p = service.verify(u.getId(), stub(u, Store.APPLE, ref, 1, 1));
        assertThat(p.refundStatus()).isEqualTo(Refund.REFUNDED);
        assertThat(p.grantedSeconds()).isZero();
        assertThat(p.ledgerIds()).isEmpty();
        assertThat(ledger.balance(u.getId())).isZero();
    }
    @Test void signedRefundReversalReturnsTimeOnceAndOlderRefundIsIgnored() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        var p = service.verify(u.getId(), stub(u, Store.APPLE, ref, 1, 1));
        service.notification(notification(Store.APPLE, ref, Action.REVOKE, 1000));
        var reversed = notification(Store.APPLE, ref, Action.RESTORE, 2000);
        service.notification(reversed); service.notification(reversed);
        service.notification(notification(Store.APPLE, ref, Action.REVOKE, 1500));
        assertThat(ledger.balance(u.getId())).isEqualTo(600);
        assertThat(reads.payment(p.id()).refundStatus()).isEqualTo(Refund.REVERSED);
        assertThat(reads.payment(p.id()).ledgerIds()).hasSize(3);
    }
    @Test void progressiveQuantityRefundsUseDistinctJournalEntries() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        var req = stub(u, Store.GOOGLE, ref, 3, 3); var p = service.verify(u.getId(), req);
        stub(u, Store.GOOGLE, ref, 3, 2); service.verify(u.getId(), req);
        stub(u, Store.GOOGLE, ref, 3, 1); service.verify(u.getId(), req);
        assertThat(ledger.balance(u.getId())).isEqualTo(60);
        assertThat(reads.payment(p.id()).revokedSeconds()).isEqualTo(120);
        assertThat(reads.payment(p.id()).ledgerIds()).hasSize(3);
        assertThat(reads.jobs(p.id(), 0, 20).getTotalElements()).isEqualTo(1);
    }
    @Test void applePercentageRefundAndNewRefundAfterReversalAreReconciled() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        var req = stub(u, Store.APPLE, ref, 1, 1); service.verify(u.getId(), req);
        doReturn(new StoreGateway.Purchase(Store.APPLE, ref, "order-" + ref, "time.apple",
                u.getUuid(), 1, 1, true, false, true, 25, 1000)).when(store).verify(Store.APPLE, ref);
        service.verify(u.getId(), req);
        assertThat(ledger.balance(u.getId())).isEqualTo(450);
        stub(u, Store.APPLE, ref, 1, 1);
        service.notification(notification(Store.APPLE, ref, Action.RESTORE, 2000));
        assertThat(ledger.balance(u.getId())).isEqualTo(600);
        doReturn(new StoreGateway.Purchase(Store.APPLE, ref, "order-" + ref, "time.apple",
                u.getUuid(), 1, 0, true, true, true, 100, 3000)).when(store).verify(Store.APPLE, ref);
        service.verify(u.getId(), req);
        assertThat(ledger.balance(u.getId())).isZero();
    }
    @Test void ownershipAndCanonicalOrderUniquenessPreventDoubleCredits() {
        var u = user(UserRole.USER, 0); var other = user(UserRole.USER, 0);
        String ref = UUID.randomUUID().toString(); var req = stub(u, Store.APPLE, ref, 1, 1);
        assertThatThrownBy(() -> service.verify(other.getId(), req)).isInstanceOf(GeneralException.class);
        service.verify(u.getId(), req);
        String alias = UUID.randomUUID().toString();
        var original = purchase(u, Store.APPLE, ref, 1, 1);
        when(store.verify(Store.APPLE, alias)).thenReturn(new StoreGateway.Purchase(Store.APPLE, alias, original.transactionId(),
                original.productId(), u.getUuid(), 1, 1, true, false, true));
        assertThatThrownBy(() -> service.verify(u.getId(), new Verify(Store.APPLE, alias))).isInstanceOf(GeneralException.class);
        assertThat(ledger.balance(u.getId())).isEqualTo(600);
    }
    @Test void adjustmentsHaveSeparatePermissionPayloadAwareIdempotencyAndAudit() {
        var u = user(UserRole.USER, 100); var admin = user(UserRole.ADMIN, 0); var root = user(UserRole.SUPER_ADMIN, 0);
        var req = new Adjustment(u.getId(), 50, "고객문의 시간 반환", "case-one");
        assertThatThrownBy(() -> adjustments.adjust(admin.getId(), req)).isInstanceOf(GeneralException.class);
        var result = adjustments.adjust(root.getId(), req);
        assertThat(adjustments.adjust(root.getId(), req).id()).isEqualTo(result.id());
        assertThatThrownBy(() -> adjustments.adjust(root.getId(), new Adjustment(u.getId(), 51, req.reason(), req.idempotencyKey())))
                .isInstanceOf(GeneralException.class);
        assertThat(ledger.balance(u.getId())).isEqualTo(150);
        assertThat(adminAudits.findAll().stream().anyMatch(a -> "TALK_TIME_ADJUSTMENT".equals(a.getAction())
                && root.getId().equals(a.getStaffId()) && "SUCCESS".equals(a.getResult()))).isTrue();
    }
    @Test void failedStoreNotificationIsDurableAndOperatorRetryIsIdempotent() {
        var u = user(UserRole.USER, 0); var root = user(UserRole.SUPER_ADMIN, 0);
        String ref = UUID.randomUUID().toString(); var p = service.verify(u.getId(), stub(u, Store.GOOGLE, ref, 2, 2));
        when(store.verify(Store.GOOGLE, ref)).thenThrow(BillingSupport.unavailable());
        service.notification(notification(Store.GOOGLE, ref, Action.REVOKE_PARTIAL, 1000));
        var failed = reads.jobs(p.id(), 0, 20).getContent().stream().filter(j -> j.action() == Action.REVOKE_PARTIAL).findFirst().orElseThrow();
        assertThat(failed.state()).isEqualTo(JobState.PENDING);
        assertThat(failed.attempts()).isEqualTo(1);
        jobService.retry(root.getId(), failed.id(), "retry-key");
        stub(u, Store.GOOGLE, ref, 2, 1); worker.process(failed.id());
        jobService.retry(root.getId(), failed.id(), "retry-key");
        assertThat(jobs.findById(failed.id()).orElseThrow().getState()).isEqualTo(JobState.COMPLETED);
        assertThat(ledger.balance(u.getId())).isEqualTo(60);
    }
    @Test void expiredLeaseIsFencedSoOldWorkerCannotCompleteNewAttempt() {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        Long id = registry.reserve(Store.APPLE, ref);
        Long job = registry.notification(notification(Store.APPLE, ref, Action.SYNC, 1000), id);
        var first = jobService.claim(job);
        mutate(() -> org.springframework.test.util.ReflectionTestUtils.setField(jobs.lock(job).orElseThrow(),
                "leaseUntil", LocalDateTime.now().minusSeconds(1)));
        var second = jobService.claim(job);
        var p = purchase(u, Store.APPLE, ref, 1, 1);
        jobService.finish(first, p);
        assertThat(ledger.balance(u.getId())).isZero();
        jobService.finish(second, p);
        jobService.failed(first, "LATE_FAILURE");
        assertThat(jobs.findById(job).orElseThrow().getState()).isEqualTo(JobState.COMPLETED);
        assertThat(ledger.balance(u.getId())).isEqualTo(600);
    }
    @Test void simultaneousReceiptRequestsProduceExactlyOneGrant() throws Exception {
        var u = user(UserRole.USER, 0); String ref = UUID.randomUUID().toString();
        var req = stub(u, Store.APPLE, ref, 1, 1); var pool = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<PaymentView>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> { gate.await(); return service.verify(u.getId(), req); }));
            gate.countDown(); Set<Long> ids = new HashSet<>();
            for (var f : futures) ids.add(f.get(30, TimeUnit.SECONDS).id());
            assertThat(ids).hasSize(1);
            assertThat(ledger.balance(u.getId())).isEqualTo(600);
            assertThat(reads.payment(ids.iterator().next()).ledgerIds()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void simultaneousDistinctPaymentsForSameMemberPreserveBothCredits() throws Exception {
        var u = user(UserRole.USER, 0);
        var first = stub(u, Store.APPLE, UUID.randomUUID().toString(), 1, 1);
        var second = stub(u, Store.APPLE, UUID.randomUUID().toString(), 1, 1);
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { gate.await(); return service.verify(u.getId(), first); });
            var b = pool.submit(() -> { gate.await(); return service.verify(u.getId(), second); });
            gate.countDown(); a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
            assertThat(ledger.balance(u.getId())).isEqualTo(1200);
            assertThat(users.findById(u.getId()).orElseThrow().getRemainingTalkTime()).isEqualTo(1200);
        } finally { pool.shutdownNow(); }
    }
}
