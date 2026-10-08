package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequestFile;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobVerificationEvidenceCleanupService {

    private final JobVerificationRequestRepository requestRepository;
    private final FileService fileService;

    @Transactional
    public void deleteForUsers(List<Long> userIds) {
        List<JobVerificationRequest> requests = requestRepository.findByUser_IdIn(userIds);
        for (JobVerificationRequest request : requests) {
            for (JobVerificationRequestFile file : request.getFiles()) {
                fileService.deleteJobCertificationImage(
                        request.getUser().getUuid(),
                        new FileService.VerifiedJobCertificationImage(
                                file.getBucket(),
                                file.getObjectKey(),
                                file.getObjectVersionId(),
                                file.getObjectEtag()
                        )
                );
            }
        }
        requestRepository.deleteAll(requests);
    }
}
