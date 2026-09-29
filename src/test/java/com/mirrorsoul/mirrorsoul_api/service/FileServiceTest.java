package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlResDTO;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class FileServiceTest {

    private S3Presigner s3Presigner;
    private S3Client s3Client;
    private FileService service;

    @BeforeEach
    void setUp() {
        s3Presigner = mock(S3Presigner.class);
        s3Client = mock(S3Client.class);
        AwsS3Properties properties = new AwsS3Properties();
        properties.setBucket("test-bucket");
        properties.setRegion("ap-northeast-2");
        properties.setPresignedUrlExpirationMinutes(5);
        service = new FileService(s3Presigner, s3Client, properties);
    }

    @Test
    void createsPresignedUrlForProfileImage() throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(URI.create("https://upload.example.com/signed").toURL());
        UUID userUuid = UUID.randomUUID();

        PresignedUrlResDTO result = service.createPresignedUrl(
                userUuid,
                new PresignedUrlReqDTO("my profile.png", "image/png", "profile-images")
        );

        assertThat(result.presignedUrl()).isEqualTo("https://upload.example.com/signed");
        assertThat(result.objectKey())
                .startsWith("profile-images/" + userUuid + "/")
                .endsWith("-my-profile.png");
        assertThat(result.fileUrl()).isEqualTo(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + result.objectKey()
        );
    }

    @Test
    void rejectsUnsupportedProfileImageContentType() {
        UUID userUuid = UUID.randomUUID();

        assertThatThrownBy(() -> service.createPresignedUrl(
                userUuid,
                new PresignedUrlReqDTO("profile.gif", "image/gif", "profile-images")
        )).isInstanceOfSatisfying(
                GeneralException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(GeneralErrorCode.INVALID_PARAMETER)
        );

        verifyNoInteractions(s3Presigner, s3Client);
    }

    @Test
    void verifiesUploadedProfileImageAndBuildsFileUrl() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "profile-images/" + userUuid + "/profile.webp";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/webp")
                        .contentLength(1024L)
                        .build()
        );

        FileService.VerifiedS3Object result =
                service.verifyProfileImageAndBuildFileUrl(userUuid, objectKey);

        assertThat(result.objectKey()).isEqualTo(objectKey);
        assertThat(result.fileUrl()).isEqualTo(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + objectKey
        );
    }

    @Test
    void rejectsProfileImageOwnedByAnotherUser() {
        UUID userUuid = UUID.randomUUID();
        String otherUsersObjectKey = "profile-images/" + UUID.randomUUID() + "/profile.png";

        assertThatThrownBy(() ->
                service.verifyProfileImageAndBuildFileUrl(userUuid, otherUsersObjectKey)
        ).isInstanceOfSatisfying(
                GeneralException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(GeneralErrorCode.INVALID_PARAMETER)
        );

        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsOversizedUploadedProfileImage() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "profile-images/" + userUuid + "/profile.png";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/png")
                        .contentLength(5L * 1024 * 1024 + 1)
                        .build()
        );

        assertThatThrownBy(() ->
                service.verifyProfileImageAndBuildFileUrl(userUuid, objectKey)
        ).isInstanceOfSatisfying(
                GeneralException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(GeneralErrorCode.INVALID_PARAMETER)
        );
    }
}
