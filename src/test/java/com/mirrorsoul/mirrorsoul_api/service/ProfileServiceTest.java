package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final TalkTimeTransactionRepository talkTimeTransactionRepository =
            mock(TalkTimeTransactionRepository.class);
    private final ProfileService service = new ProfileService(
            userRepository, fileService, talkTimeTransactionRepository);

    @Test
    void buyTimeRecordsBalanceIncrease() {
        UUID userUuid = UUID.randomUUID();
        User user = User.builder().id(1L).uuid(userUuid).remainingTalkTime(1800).build();
        ProfileReqDTO.buyTimeReqDTO request = new ProfileReqDTO.buyTimeReqDTO();
        request.setBuyTime(600);
        when(userRepository.findByUuidForUpdate(userUuid)).thenReturn(Optional.of(user));

        ProfileResDTO.timeStatusDTO result = service.buyTime(userUuid, request);

        assertThat(result.getRemainingTalkTime()).isEqualTo(2400);
        ArgumentCaptor<TalkTimeTransaction> transactionCaptor =
                ArgumentCaptor.forClass(TalkTimeTransaction.class);
        verify(talkTimeTransactionRepository).save(transactionCaptor.capture());
        TalkTimeTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getReason()).isEqualTo(TalkTimeTransactionReason.TOP_UP);
        assertThat(transaction.getDeltaSeconds()).isEqualTo(600);
        assertThat(transaction.getBalanceAfterSeconds()).isEqualTo(2400);
    }

    @Test
    void getMyProfileReturnsProfileImageUrl() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getName()).thenReturn("서연");
        when(user.getEmail()).thenReturn("me@example.com");
        when(user.getProfileImageUrl()).thenReturn("https://example.com/profile.jpg");
        when(fileService.createPresignedDownloadUrlOrFallback(
                null, "https://example.com/profile.jpg"))
                .thenReturn("https://example.com/profile.jpg");

        ProfileResDTO.myProfileDTO result = service.getMyProfile(userUuid);

        assertThat(result.getName()).isEqualTo("서연");
        assertThat(result.getEmail()).isEqualTo("me@example.com");
        assertThat(result.getProfileImageUrl()).isEqualTo("https://example.com/profile.jpg");
    }

    @Test
    void getMyProfileReturnsPresignedProfileImageUrlWhenObjectKeyExists() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        String objectKey = "profile-images/" + userUuid + "/profile.png";
        String storedUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + objectKey;
        String presignedUrl = storedUrl + "?X-Amz-Signature=test";

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getName()).thenReturn("서연");
        when(user.getEmail()).thenReturn("me@example.com");
        when(user.getProfileImageObjectKey()).thenReturn(objectKey);
        when(user.getProfileImageUrl()).thenReturn(storedUrl);
        when(fileService.createPresignedDownloadUrlOrFallback(objectKey, storedUrl))
                .thenReturn(presignedUrl);

        ProfileResDTO.myProfileDTO result = service.getMyProfile(userUuid);

        assertThat(result.getProfileImageUrl()).isEqualTo(presignedUrl);
    }

    @Test
    void modifyProfileImageRegistersOrReplacesVerifiedImage() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        String objectKey = "profile-images/" + userUuid + "/profile.png";
        String fileUrl = "https://example.com/profile.png";
        ProfileReqDTO.modifyProfileImageReqDTO request =
                new ProfileReqDTO.modifyProfileImageReqDTO();
        request.setObjectKey(objectKey);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(fileService.verifyProfileImageAndBuildFileUrl(userUuid, objectKey))
                .thenReturn(new FileService.VerifiedS3Object(fileUrl, objectKey));
        when(fileService.createPresignedDownloadUrlOrFallback(objectKey, fileUrl))
                .thenReturn(fileUrl + "?X-Amz-Signature=test");

        ProfileResDTO.ProfileImageDTO result = service.modifyProfileImage(userUuid, request);

        assertThat(result.profileImageUrl()).isEqualTo(fileUrl + "?X-Amz-Signature=test");
        verify(user).updateProfileImage(fileUrl, objectKey);
    }

    @Test
    void deleteProfileImageClearsStoredImage() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));

        service.deleteProfileImage(userUuid);

        verify(user).clearProfileImage();
    }
}
