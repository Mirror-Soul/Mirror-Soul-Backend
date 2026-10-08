package com.mirrorsoul.mirrorsoul_api.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobVerificationEvidenceCleanupTest {

    @Test
    void removesStoredPhotosAndRequestBeforeAccountAnonymization() {
        JobVerificationRequestRepository requests = mock(JobVerificationRequestRepository.class);
        FileService files = mock(FileService.class);
        JobVerificationEvidenceCleanupService cleanup =
                new JobVerificationEvidenceCleanupService(requests, files);
        UUID userUuid = UUID.randomUUID();
        User user = User.builder().id(1L).uuid(userUuid).build();
        JobVerificationRequest request =
                JobVerificationRequest.pending(user, Job.IT_TECH);
        String key = "job-certifications/" + userUuid + "/photo.jpg";
        request.addFile("bucket", key, "version-1", "\"etag\"", 0);
        when(requests.findByUser_IdIn(List.of(1L))).thenReturn(List.of(request));

        cleanup.deleteForUsers(List.of(1L));

        verify(files).deleteJobCertificationImage(userUuid,
                new FileService.VerifiedJobCertificationImage("bucket", key, "version-1", "\"etag\""));
        verify(requests).deleteAll(List.of(request));
    }
}
