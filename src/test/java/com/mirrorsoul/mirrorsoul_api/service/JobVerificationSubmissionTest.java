package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationSubmitRequest;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class JobVerificationSubmissionTest {

    @Test
    void createsPendingRequestForCurrentJobAndVerifiedImage() {
        UserRepository users = mock(UserRepository.class);
        JobVerificationRequestRepository requests = mock(JobVerificationRequestRepository.class);
        JobVerificationRequestWriter writer = new JobVerificationRequestWriter(users, requests);
        User user = user(Job.IT_TECH);
        when(users.findByUuidForUpdate(user.getUuid())).thenReturn(Optional.of(user));
        when(requests.findByUser_IdAndStatus(1L, JobVerificationRequestStatus.PENDING))
                .thenReturn(List.of());
        when(requests.save(any(JobVerificationRequest.class))).thenAnswer(invocation -> {
            JobVerificationRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 42L);
            return request;
        });
        JobVerificationRequest.FileSnapshot image = new JobVerificationRequest.FileSnapshot(
                "bucket", "job-certifications/" + user.getUuid() + "/photo.png", "version-1", "\"etag\""
        );

        var response = writer.createPending(user.getUuid(), List.of(image));

        assertThat(response.requestId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(JobVerificationRequestStatus.PENDING);
        assertThat(response.claimedJob()).isEqualTo(Job.IT_TECH);
        ArgumentCaptor<JobVerificationRequest> captor = ArgumentCaptor.forClass(JobVerificationRequest.class);
        verify(requests).save(captor.capture());
        assertThat(captor.getValue().getClaimedJob()).isEqualTo(Job.IT_TECH);
        assertThat(captor.getValue().getFiles()).hasSize(1);
        assertThat(captor.getValue().getFiles().get(0).getObjectVersionId()).isEqualTo("version-1");
    }

    @Test
    void retryWithSameEvidenceReturnsExistingPendingRequest() {
        UserRepository users = mock(UserRepository.class);
        JobVerificationRequestRepository requests = mock(JobVerificationRequestRepository.class);
        JobVerificationRequestWriter writer = new JobVerificationRequestWriter(users, requests);
        User user = user(Job.IT_TECH);
        JobVerificationRequest.FileSnapshot image = new JobVerificationRequest.FileSnapshot(
                "bucket", "job-certifications/" + user.getUuid() + "/photo.png", null, "\"etag\""
        );
        JobVerificationRequest existing = JobVerificationRequest.pending(user, Job.IT_TECH);
        existing.addFile(image.bucket(), image.objectKey(), image.versionId(), image.etag(), 0);
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(users.findByUuidForUpdate(user.getUuid())).thenReturn(Optional.of(user));
        when(requests.findByUser_IdAndStatus(1L, JobVerificationRequestStatus.PENDING))
                .thenReturn(List.of(existing));

        var response = writer.createPending(user.getUuid(), List.of(image));

        assertThat(response.requestId()).isEqualTo(7L);
        verify(requests, never()).save(any());
    }

    @Test
    void differentEvidenceCannotCreateAnotherPendingRequest() {
        UserRepository users = mock(UserRepository.class);
        JobVerificationRequestRepository requests = mock(JobVerificationRequestRepository.class);
        JobVerificationRequestWriter writer = new JobVerificationRequestWriter(users, requests);
        User user = user(Job.IT_TECH);
        JobVerificationRequest existing = JobVerificationRequest.pending(user, Job.IT_TECH);
        existing.addFile("bucket", "old-key", null, null, 0);
        when(users.findByUuidForUpdate(user.getUuid())).thenReturn(Optional.of(user));
        when(requests.findByUser_IdAndStatus(1L, JobVerificationRequestStatus.PENDING))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> writer.createPending(user.getUuid(),
                List.of(new JobVerificationRequest.FileSnapshot("bucket", "new-key", null, null))))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.JOB_VERIFICATION_ALREADY_PENDING));
        verify(requests, never()).save(any());
    }

    @Test
    void jobMustBeSelectedBeforeSubmission() {
        UserRepository users = mock(UserRepository.class);
        JobVerificationRequestRepository requests = mock(JobVerificationRequestRepository.class);
        JobVerificationRequestWriter writer = new JobVerificationRequestWriter(users, requests);
        User user = user(null);
        when(users.findByUuidForUpdate(user.getUuid())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> writer.createPending(user.getUuid(), List.of()))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        verify(requests, never()).save(any());
    }

    @Test
    void duplicateImageKeyIsRejectedBeforeWritingRequest() {
        FileService files = mock(FileService.class);
        JobVerificationRequestWriter writer = mock(JobVerificationRequestWriter.class);
        JobVerificationSubmissionService service = new JobVerificationSubmissionService(files, writer);
        UUID userUuid = UUID.randomUUID();
        String key = "job-certifications/" + userUuid + "/photo.jpg";
        when(files.verifyJobCertificationImage(userUuid, key))
                .thenReturn(new FileService.VerifiedJobCertificationImage("bucket", key, null, null));

        assertThatThrownBy(() -> service.submit(userUuid,
                new JobVerificationSubmitRequest(List.of(key, key))))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        verify(writer, never()).createPending(any(), any());
    }

    private User user(Job job) {
        return User.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .status(UserStatus.ONBOARD_B)
                .job(job)
                .build();
    }
}
