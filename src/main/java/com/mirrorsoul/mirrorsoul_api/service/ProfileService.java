package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository userRepository;
    private final FileService fileService;

    public ProfileResDTO.myProfileDTO getMyProfile(UUID userUuid) {
        User user = getUser(userUuid);

        return ProfileResDTO.myProfileDTO.builder()
                .name(user.getName())
                .email(user.getEmail())
                .profileImageUrl(profileImageUrl(user))
                .build();
    }

    public ProfileResDTO.timeStatusDTO getMyTime(UUID userUuid) {
        User user = getUser(userUuid);
        return toTimeStatus(user.getRemainingTalkTime());
    }

    @Transactional
    public ProfileResDTO.timeStatusDTO buyTime(UUID userUuid, ProfileReqDTO.buyTimeReqDTO request) {
        User user = getUser(userUuid);
        user.addTalkTime(request.getBuyTime());

        return toTimeStatus(user.getRemainingTalkTime());
    }

    public ProfileResDTO.audioSettingsDTO getAudioSettings(UUID userUuid) {
        User user = getUser(userUuid);

        return ProfileResDTO.audioSettingsDTO.builder()
                .opponentVoiceVolume(user.getOpponentVoiceVolume())
                .opponentSpeechSpeed(user.getOpponentSpeechSpeed())
                .build();
    }

    @Transactional
    public ProfileResDTO.audioSettingsDTO updateAudioSettings(UUID userUuid, ProfileReqDTO.audioSettingsReqDTO request) {
        User user = getUser(userUuid);
        user.updateAudioSettings(request.getOpponentVoiceVolume(), request.getOpponentSpeechSpeed());

        return ProfileResDTO.audioSettingsDTO.builder()
                .opponentVoiceVolume(user.getOpponentVoiceVolume())
                .opponentSpeechSpeed(user.getOpponentSpeechSpeed())
                .build();
    }

    public ProfileResDTO.alarmSettingDTO getAlarmSetting(UUID userUuid) {
        User user = getUser(userUuid);

        return ProfileResDTO.alarmSettingDTO.builder()
                .missedCallNotificationEnabled(user.getMissedCallNotificationEnabled())
                .lowTimeNotificationEnabled(user.getLowTimeNotificationEnabled())
                .build();
    }

    @Transactional
    public ProfileResDTO.alarmSettingDTO modifyAlarmSetting(UUID userUuid, ProfileReqDTO.alarmSettingReqDTO request) {
        User user = getUser(userUuid);
        user.updateAlarmSettings(
                request.getMissedCallNotificationEnabled(),
                request.getLowTimeNotificationEnabled()
        );

        return ProfileResDTO.alarmSettingDTO.builder()
                .missedCallNotificationEnabled(user.getMissedCallNotificationEnabled())
                .lowTimeNotificationEnabled(user.getLowTimeNotificationEnabled())
                .build();
    }

    public ProfileResDTO.accountInfoDTO getAccountInfo(UUID userUuid) {
        User user = getUser(userUuid);

        return ProfileResDTO.accountInfoDTO.builder()
                .name(user.getName())
                .build();
    }

    @Transactional
    public void modifyNickname(UUID userUuid, ProfileReqDTO.modifyNicknameReqDTO request) {
        User user = getUser(userUuid);
        String nickname = request.getNickname().trim();

        if (!nickname.equals(user.getName()) && userRepository.existsByName(nickname)) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_NICKNAME);
        }

        user.setName(nickname);
    }

    @Transactional
    public ProfileResDTO.ProfileImageDTO modifyProfileImage(
            UUID userUuid,
            ProfileReqDTO.modifyProfileImageReqDTO request
    ) {
        User user = getUser(userUuid);
        FileService.VerifiedS3Object image = fileService.verifyProfileImageAndBuildFileUrl(
                userUuid,
                request.getObjectKey()
        );
        user.updateProfileImage(image.fileUrl(), image.objectKey());
        return new ProfileResDTO.ProfileImageDTO(
                fileService.createPresignedDownloadUrlOrFallback(image.objectKey(), image.fileUrl())
        );
    }

    @Transactional
    public void deleteProfileImage(UUID userUuid) {
        User user = getUser(userUuid);
        user.clearProfileImage();
    }

    @Transactional
    public void inactiveAccount(UUID userUuid) {
        User user = getUser(userUuid);
        user.deactivate(LocalDateTime.now());
    }

    private User getUser(UUID userUuid) {
        return userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private String profileImageUrl(User user) {
        return fileService.createPresignedDownloadUrlOrFallback(
                user.getProfileImageObjectKey(),
                user.getProfileImageUrl()
        );
    }

    private ProfileResDTO.timeStatusDTO toTimeStatus(Integer remainingTalkTime) {
        int safeRemainingTime = Math.max(remainingTalkTime == null ? 0 : remainingTalkTime, 0);
        int hours = safeRemainingTime / 3600;
        int minutes = (safeRemainingTime % 3600) / 60;
        int seconds = safeRemainingTime % 60;

        return ProfileResDTO.timeStatusDTO.builder()
                .remainingTalkTime(safeRemainingTime)
                .hours(hours)
                .minutes(minutes)
                .seconds(seconds)
                .build();
    }
}
