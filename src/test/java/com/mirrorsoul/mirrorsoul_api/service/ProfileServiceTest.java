package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final ProfileService service = new ProfileService(userRepository, fileService);

    @Test
    void getMyProfileReturnsProfileImageUrl() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getName()).thenReturn("서연");
        when(user.getEmail()).thenReturn("me@example.com");
        when(user.getProfileImageUrl()).thenReturn("https://example.com/profile.jpg");

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
