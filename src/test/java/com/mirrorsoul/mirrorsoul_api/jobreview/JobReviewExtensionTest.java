package com.mirrorsoul.mirrorsoul_api.jobreview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.service.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({JobVerificationReviewService.class,JobReviewExtensionService.class,JobReviewLifecycleWorker.class,JpaAuditingConfig.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class JobReviewExtensionTest {
    @Autowired JobVerificationReviewService reviews;
    @Autowired JobReviewExtensionService extension;
    @Autowired JobReviewLifecycleWorker worker;
    @Autowired JobVerificationRequestRepository requests;
    @Autowired UserRepository users;
    @MockitoBean FileService files;
    @MockitoBean PushNotificationService pushes;
    @MockitoBean PushDeviceRepository devices;
    User user(UserRole role) {
        var uuid=UUID.randomUUID();
        return users.saveAndFlush(User.builder().uuid(uuid).email(uuid+"@example.test")
                .passwordHash("hash").status(UserStatus.ACTIVE).role(role).job(Job.IT_TECH).build());
    }
    JobVerificationRequest pending(User owner) {
        var r=JobVerificationRequest.pending(owner,Job.IT_TECH);
        r.addFile("bucket","job-certifications/"+owner.getUuid()+"/image","v1","etag",0);
        return requests.saveAndFlush(r);
    }
    @Test void historyFiltersAndProcessedEvidencePermissionUseReviewerAndSuperAdmin() {
        var owner=user(UserRole.USER);var reviewer=user(UserRole.ADMIN);var other=user(UserRole.ADMIN);var root=user(UserRole.SUPER_ADMIN);
        var r=pending(owner);
        when(files.createJobVerificationReviewAccess(any(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(new FileService.JobVerificationReviewAccess("https://example.test/photo",Map.of()));
        reviews.approve(reviewer.getUuid(),r.getId());
        var lifecycle=extension.lifecycle(r.getId());
        assertThat(lifecycle.notificationState()).isEqualTo("PENDING");
        assertThat(lifecycle.evidenceExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(extension.history(JobVerificationRequestStatus.APPROVED,reviewer.getId(),null,null,r.getId(),lifecycle.version(),0,20).getTotalElements()).isEqualTo(1);
        assertThat(extension.history(null,other.getId(),null,null,r.getId(),null,0,20).isEmpty()).isTrue();
        assertThat(reviews.detail(reviewer.getUuid(),r.getId()).photos()).hasSize(1);
        assertThat(reviews.detail(root.getUuid(),r.getId()).photos()).hasSize(1);
        assertThatThrownBy(()->reviews.detail(other.getUuid(),r.getId())).isInstanceOf(GeneralException.class);
        assertThatThrownBy(()->reviews.detail(owner.getUuid(),r.getId())).isInstanceOf(GeneralException.class);
        assertThatThrownBy(()->reviews.detail(r.getId())).isInstanceOf(GeneralException.class);
        assertThatThrownBy(()->reviews.reject(root.getUuid(),r.getId(),"repeat")).isInstanceOf(GeneralException.class);
    }
    @Test void expiryBlocksViewingAndApprovalAndDeletionFailuresRemainRetryable() {
        var owner=user(UserRole.USER);var root=user(UserRole.SUPER_ADMIN);var r=pending(owner);
        org.springframework.test.util.ReflectionTestUtils.setField(r,"evidenceExpiresAt",LocalDateTime.now().minusMinutes(1));
        requests.saveAndFlush(r);
        assertThat(reviews.detail(root.getUuid(),r.getId()).photos()).isEmpty();
        assertThatThrownBy(()->reviews.approve(root.getUuid(),r.getId())).isInstanceOf(GeneralException.class);
        doThrow(new IllegalStateException("private storage detail")).when(files).deleteJobCertificationSnapshot(any(),any());
        worker.delete(r.getId());
        assertThat(extension.lifecycle(r.getId()).deletionError()).isEqualTo("STORAGE_DELETE_FAILED");
        assertThat(extension.lifecycle(r.getId()).evidenceState()).isEqualTo("EXPIRED");
        doNothing().when(files).deleteJobCertificationSnapshot(any(),any());
        worker.delete(r.getId());worker.delete(r.getId());
        assertThat(extension.lifecycle(r.getId()).evidenceState()).isEqualTo("DELETED");
        assertThat(extension.lifecycle(r.getId()).deletionAttempts()).isEqualTo(2);
        verify(files,times(2)).deleteJobCertificationSnapshot(eq(owner.getUuid()),any());
    }
    @Test void durableSendRecordsSuccessAndDoesNotSendTwice() {
        var owner=user(UserRole.USER);var r=pending(owner);reviews.approve(user(UserRole.SUPER_ADMIN).getUuid(),r.getId());
        when(devices.findAllByUserUuidInAndEnabledTrue(anyList())).thenReturn(List.of(mock(PushDevice.class)));
        worker.deliver(r.getId());worker.deliver(r.getId());
        assertThat(extension.lifecycle(r.getId()).notificationState()).isEqualTo("SENT");
        assertThat(extension.lifecycle(r.getId()).notificationAttempts()).isEqualTo(1);
        verify(pushes,times(1)).sendJobVerificationResult(any());
    }
    @Test void uncertainSendNeedsExplicitDuplicateAcknowledgementAndFreshVersionForRetry() {
        var owner=user(UserRole.USER);var r=pending(owner);reviews.reject(user(UserRole.ADMIN).getUuid(),r.getId(),"blurred");
        when(devices.findAllByUserUuidInAndEnabledTrue(anyList())).thenReturn(List.of(mock(PushDevice.class)));
        doThrow(new IllegalStateException("transport timeout")).when(pushes).sendJobVerificationResult(any());
        worker.deliver(r.getId());
        var result=extension.lifecycle(r.getId());assertThat(result.notificationState()).isEqualTo("UNKNOWN");
        worker.deliver(r.getId());verify(pushes,times(1)).sendJobVerificationResult(any());
        assertThatThrownBy(()->extension.retryNotification(r.getId(),result.version(),false)).isInstanceOf(GeneralException.class);
        extension.retryNotification(r.getId(),result.version(),true);
        assertThatThrownBy(()->extension.retryNotification(r.getId(),result.version(),true)).isInstanceOf(GeneralException.class);
        doNothing().when(pushes).sendJobVerificationResult(any());worker.deliver(r.getId());
        assertThat(extension.lifecycle(r.getId()).notificationState()).isEqualTo("SENT");
        assertThat(extension.lifecycle(r.getId()).notificationAttempts()).isEqualTo(2);
    }
    @Test void partialFailureAndNoDeviceAreDistinguished() {
        var owner=user(UserRole.USER);var r=pending(owner);reviews.approve(user(UserRole.ADMIN).getUuid(),r.getId());
        when(devices.findAllByUserUuidInAndEnabledTrue(anyList())).thenReturn(List.of());
        worker.deliver(r.getId());assertThat(extension.lifecycle(r.getId()).notificationState()).isEqualTo("NO_DEVICE");
        extension.retryNotification(r.getId(),extension.lifecycle(r.getId()).version(),false);
        when(devices.findAllByUserUuidInAndEnabledTrue(anyList())).thenReturn(List.of(mock(PushDevice.class)));
        doThrow(new JobNotificationDeliveryException()).when(pushes).sendJobVerificationResult(any());
        worker.deliver(r.getId());assertThat(extension.lifecycle(r.getId()).notificationState()).isEqualTo("FAILED");
    }
    @Test void interruptedWorkerDoesNotAutomaticallyResend() {
        var owner=user(UserRole.USER);var r=pending(owner);reviews.approve(user(UserRole.ADMIN).getUuid(),r.getId());
        r=requests.findById(r.getId()).orElseThrow();r.notification("SENDING",null,LocalDateTime.now().minusMinutes(11));requests.saveAndFlush(r);
        worker.deliver(r.getId());assertThat(extension.lifecycle(r.getId()).notificationState()).isEqualTo("UNKNOWN");verifyNoInteractions(pushes);
    }
    @Test void oldDeliveryCompletionCannotOverwriteANewClaim() {
        var owner=user(UserRole.USER);var r=pending(owner);reviews.approve(user(UserRole.ADMIN).getUuid(),r.getId());
        when(devices.findAllByUserUuidInAndEnabledTrue(anyList())).thenReturn(List.of(mock(PushDevice.class)));
        doAnswer(invocation -> {
            var current=requests.findById(r.getId()).orElseThrow();
            current.notification("UNKNOWN","DELIVERY_INTERRUPTED",LocalDateTime.now());requests.saveAndFlush(current);
            extension.retryNotification(r.getId(),extension.lifecycle(r.getId()).version(),true);
            current=requests.findById(r.getId()).orElseThrow();current.notification("SENDING",null,LocalDateTime.now());requests.saveAndFlush(current);
            return null;
        }).when(pushes).sendJobVerificationResult(any());
        worker.deliver(r.getId());
        assertThat(extension.lifecycle(r.getId()).notificationState()).isEqualTo("SENDING");
        assertThat(extension.lifecycle(r.getId()).notificationAttempts()).isEqualTo(2);
    }
    @Test void staleDecisionVersionAndExpiredRejectionCannotResurrectPhotos() {
        var owner=user(UserRole.USER);var admin=user(UserRole.ADMIN);var r=pending(owner);
        assertThatThrownBy(()->reviews.approve(admin.getUuid(),r.getId(),99L)).isInstanceOf(GeneralException.class);
        org.springframework.test.util.ReflectionTestUtils.setField(r,"evidenceExpiresAt",LocalDateTime.now().minusMinutes(1));requests.saveAndFlush(r);
        reviews.reject(admin.getUuid(),r.getId(),"expired");
        assertThat(extension.lifecycle(r.getId()).evidenceState()).isEqualTo("EXPIRED");
        assertThat(reviews.detail(admin.getUuid(),r.getId()).photos()).isEmpty();
    }
    @Test void invalidHistoryFiltersAreRejected() {
        assertThatThrownBy(()->extension.history(JobVerificationRequestStatus.PENDING,null,null,null,null,null,0,20)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(()->extension.history(null,null,LocalDateTime.now(),LocalDateTime.now().minusDays(1),null,null,0,20)).isInstanceOf(GeneralException.class);
    }
}
