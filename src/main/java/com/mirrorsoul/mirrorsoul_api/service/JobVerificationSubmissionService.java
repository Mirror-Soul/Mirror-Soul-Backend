package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationSubmitRequest;
import com.mirrorsoul.mirrorsoul_api.dto.jobverification.JobVerificationSubmitResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobVerificationSubmissionService {

    private final FileService fileService;
    private final JobVerificationRequestWriter requestWriter;

    public JobVerificationSubmitResponse submit(UUID userUuid, JobVerificationSubmitRequest request) {
        List<JobVerificationRequest.FileSnapshot> snapshots = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (String objectKey : request.objectKeys()) {
            FileService.VerifiedJobCertificationImage image =
                    fileService.verifyJobCertificationImage(userUuid, objectKey);
            if (!seenKeys.add(image.objectKey())) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,
                        "The same job certification image was submitted twice.");
            }
            snapshots.add(new JobVerificationRequest.FileSnapshot(
                    image.bucket(), image.objectKey(), image.objectVersionId(), image.objectEtag()
            ));
        }
        return requestWriter.createPending(userUuid, snapshots);
    }
}
