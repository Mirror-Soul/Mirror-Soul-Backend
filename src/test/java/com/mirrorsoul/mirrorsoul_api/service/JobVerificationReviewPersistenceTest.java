package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({JobVerificationReviewService.class, JobVerificationDisplayService.class,
        JpaAuditingConfig.class})
class JobVerificationReviewPersistenceTest {

    @Autowired private JobVerificationReviewService service;
    @Autowired private JobVerificationDisplayService displayService;
    @Autowired private JobVerificationRequestRepository requests;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @MockitoBean private FileService files;

    @Test
    void pendingDetailAndApprovalPersistWithoutGrantingFinalVerification() {
        User applicant = user(UserRole.USER);
        User admin = user(UserRole.ADMIN);
        JobVerificationRequest request = pending(applicant);
        when(files.createJobVerificationReviewAccess(
                applicant.getUuid(), "bucket", "job-certifications/photo", "v1", "etag"))
                .thenReturn(new FileService.JobVerificationReviewAccess(
                        "https://example.test/photo", Map.of()));

        assertThat(service.pending(0, 20).getContent()).hasSize(1);
        var detail = service.detail(request.getId());
        assertThat(detail.userUuid()).isEqualTo(applicant.getUuid());
        assertThat(detail.photos()).extracting(photo -> photo.reviewUrl())
                .containsExactly("https://example.test/photo");

        var decision = service.approve(admin.getUuid(), request.getId());
        entityManager.flush();
        entityManager.clear();

        JobVerificationRequest reviewed = requests.findById(request.getId()).orElseThrow();
        assertThat(decision.status()).isEqualTo(JobVerificationRequestStatus.APPROVED);
        assertThat(reviewed.getReviewer().getId()).isEqualTo(admin.getId());
        assertThat(reviewed.getReviewedAt()).isNotNull();
        assertThat(service.pending(0, 20).getContent()).isEmpty();
        var latest = service.myLatest(applicant.getUuid());
        assertThat(latest.status()).isEqualTo(JobVerificationRequestStatus.APPROVED);
        assertThat(latest.appliesToCurrentJob()).isTrue();
        assertThat(latest.passVerificationRequired()).isTrue();
        assertThatThrownBy(() -> service.reject(admin.getUuid(), request.getId(), "다시 확인"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void rejectionStoresReasonAndPreventsRepeat() {
        User applicant = user(UserRole.USER);
        User admin = user(UserRole.ADMIN);
        JobVerificationRequest request = pending(applicant);

        assertThatThrownBy(() -> service.reject(admin.getUuid(), request.getId(), "  "))
                .isInstanceOf(GeneralException.class);
        service.reject(admin.getUuid(), request.getId(), "  사진이 흐립니다  ");
        entityManager.flush();
        entityManager.clear();

        assertThat(service.myLatest(applicant.getUuid()).rejectionReason())
                .isEqualTo("사진이 흐립니다");
        assertThatThrownBy(() -> service.approve(admin.getUuid(), request.getId()))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void ordinaryUserAndSelfReviewAreDenied() {
        User applicant = user(UserRole.USER);
        User otherUser = user(UserRole.USER);
        JobVerificationRequest request = pending(applicant);

        assertThatThrownBy(() -> service.approve(otherUser.getUuid(), request.getId()))
                .isInstanceOf(GeneralException.class);

        User adminApplicant = user(UserRole.ADMIN);
        JobVerificationRequest ownRequest = pending(adminApplicant);
        assertThatThrownBy(() -> service.approve(adminApplicant.getUuid(), ownRequest.getId()))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void displayUsesLatestReviewForCurrentJobAndIgnoresLegacyPhotoKey() {
        User applicant = user(UserRole.USER);
        applicant.setJobCertificationObjectKey("job-certifications/legacy-photo.jpg");
        User admin = user(UserRole.ADMIN);

        assertThat(displayService.hasCurrentSubmission(applicant)).isFalse();
        assertThat(displayService.documentReviewCompleted(applicant)).isFalse();

        JobVerificationRequest first = pending(applicant);
        assertThat(displayService.hasCurrentSubmission(applicant)).isTrue();
        assertThat(displayService.documentReviewCompleted(applicant)).isFalse();
        service.approve(admin.getUuid(), first.getId());
        entityManager.flush();
        assertThat(displayService.documentReviewCompleted(applicant)).isTrue();
        assertThat(displayService.documentReviewCompletedFor(List.of(applicant)))
                .containsEntry(applicant.getId(), true);

        applicant.setJob(Job.EDUCATION);
        var previousJobStatus = service.myLatest(applicant.getUuid());
        assertThat(previousJobStatus.status()).isEqualTo(JobVerificationRequestStatus.APPROVED);
        assertThat(previousJobStatus.appliesToCurrentJob()).isFalse();
        assertThat(previousJobStatus.passVerificationRequired()).isFalse();
        applicant.setJob(Job.IT_TECH);

        pending(applicant);
        entityManager.flush();
        assertThat(displayService.documentReviewCompletedFor(List.of(applicant)))
                .containsEntry(applicant.getId(), false);

        applicant.setJob(Job.EDUCATION);
        assertThat(displayService.hasCurrentSubmission(applicant)).isFalse();
    }

    private User user(UserRole role) {
        UUID uuid = UUID.randomUUID();
        return users.saveAndFlush(User.builder()
                .uuid(uuid).email(uuid + "@example.com").passwordHash("hash")
                .role(role).status(UserStatus.ACTIVE).job(Job.IT_TECH).build());
    }

    private JobVerificationRequest pending(User applicant) {
        JobVerificationRequest request = JobVerificationRequest.pending(applicant, Job.IT_TECH);
        request.addFile("bucket", "job-certifications/photo", "v1", "etag", 0);
        return requests.saveAndFlush(request);
    }
}
