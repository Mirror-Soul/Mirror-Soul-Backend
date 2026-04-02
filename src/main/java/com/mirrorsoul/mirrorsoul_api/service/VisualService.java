package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.FaceFile;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.visual.VisualReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.visual.VisualResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.FaceFileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisualService {

    private final FaceFileRepository faceFileRepository;
    private final UserRepository userRepository;

    @Transactional
    public VisualResDTO saveVisualFile(Long userId, VisualReqDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND, "User not found."));

        FaceFile faceFile = faceFileRepository.findByUser_Id(userId)
                .map(existing -> {
                    existing.updateFile(request.fileUrl(), request.objectKey());
                    return existing;
                })
                .orElseGet(() -> faceFileRepository.save(
                        FaceFile.create(user, request.fileUrl(), request.objectKey())
                ));

        return new VisualResDTO(
                faceFile.getId(),
                user.getId(),
                faceFile.getFileUrl(),
                faceFile.getObjectKey(),
                true
        );
    }
}
