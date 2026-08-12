package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.FaceFile;
import com.mirrorsoul.mirrorsoul_api.domain.FaceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.FaceTrainingJobSource;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.visual.VisualReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.visual.VisualResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.FaceFileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.event.FaceTrainingJobRequestedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import static com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.FORBIDDEN;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisualService {

    private final FaceFileRepository faceFileRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final FaceTrainingJobService faceTrainingJobService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VisualResDTO saveVisualFile(UUID userUuid, VisualReqDTO request) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND, "User not found."));

        if (!UserStatus.ONBOARD_D.equals(user.getStatus())) {
            throw new GeneralException(FORBIDDEN, "ONBOARD_D 상태의 사용자만 얼굴 이미지 파일을 저장할 수 있습니다.");
        }

        FileService.VerifiedS3Object verifiedVideo = fileService.verifyFaceVideoAndBuildFileUrl(
                userUuid,
                request.objectKey()
        );

        FaceFile faceFile = faceFileRepository.findByUser_Id(user.getId())
                .map(existing -> {
                    existing.updateFile(verifiedVideo.fileUrl(), verifiedVideo.objectKey());
                    return existing;
                })
                .orElseGet(() -> faceFileRepository.save(
                        FaceFile.create(user, verifiedVideo.fileUrl(), verifiedVideo.objectKey())
                ));

        FaceTrainingJob faceTrainingJob = faceTrainingJobService.createPendingJob(
                user,
                faceFile,
                FaceTrainingJobSource.ONBOARDING_FACE
        );
        eventPublisher.publishEvent(new FaceTrainingJobRequestedEvent(faceTrainingJob.getId()));

        user.setStatus(UserStatus.ACTIVE);

        return new VisualResDTO(
                faceFile.getId(),
                user.getUuid(),
                faceFile.getFileUrl(),
                faceFile.getObjectKey(),
                true
        );
    }
}
