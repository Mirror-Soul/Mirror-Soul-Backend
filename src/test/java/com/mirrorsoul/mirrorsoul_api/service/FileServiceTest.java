package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlResDTO;
import software.amazon.awssdk.core.ResponseBytes;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
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
    void rejectsNonImageJobCertificationUploadType() {
        UUID userUuid = UUID.randomUUID();

        assertThatThrownBy(() -> service.createPresignedUrl(
                userUuid,
                new PresignedUrlReqDTO("certificate.pdf", "application/pdf", "job-certifications")
        )).isInstanceOfSatisfying(
                GeneralException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER)
        );
        verifyNoInteractions(s3Presigner, s3Client);
    }

    @Test
    void reviewAccessUsesRecordedVersionOrEtagAndRejectsAnotherUsersKey() throws Exception {
        UUID userUuid = UUID.randomUUID();
        String key = "job-certifications/" + userUuid + "/photo.jpg";
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);
        when(presigned.url()).thenReturn(URI.create("https://download.example.com/photo").toURL());
        when(presigned.signedHeaders()).thenReturn(Map.of("If-Match", List.of("etag")));

        var access = service.createJobVerificationReviewAccess(
                userUuid, "test-bucket", key, null, "etag");
        service.createJobVerificationReviewAccess(
                userUuid, "test-bucket", key, "version-1", "etag");

        assertThat(access.signedHeaders()).containsKey("If-Match");
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignGetObject(captor.capture());
        assertThat(captor.getAllValues().get(0).getObjectRequest().ifMatch()).isEqualTo("etag");
        assertThat(captor.getAllValues().get(1).getObjectRequest().versionId()).isEqualTo("version-1");
        assertThat(captor.getAllValues().get(1).getObjectRequest().ifMatch()).isNull();
        assertThatThrownBy(() -> service.createJobVerificationReviewAccess(
                userUuid, "test-bucket", "job-certifications/other/photo.jpg", null, "etag"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void realPresignerKeepsIfMatchAsClientHeader() {
        AwsS3Properties properties = new AwsS3Properties();
        properties.setBucket("test-bucket");
        properties.setRegion("ap-northeast-2");
        UUID userUuid = UUID.randomUUID();
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("access", "secret")))
                .build()) {
            FileService realService = new FileService(presigner, s3Client, properties);
            var access = realService.createJobVerificationReviewAccess(
                    userUuid, "test-bucket", "job-certifications/" + userUuid + "/photo.jpg",
                    null, "\"saved-etag\"");

            assertThat(access.url()).contains("X-Amz-Signature");
            assertThat(access.signedHeaders()).anySatisfy((name, values) -> {
                assertThat(name).isEqualToIgnoringCase("If-Match");
                assertThat(values).containsExactly("\"saved-etag\"");
            });
            assertThat(access.signedHeaders()).doesNotContainKey("host");
        }
    }

    @Test
    void verifiesJobCertificationImageBytesAndMetadata() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "job-certifications/" + userUuid + "/certificate.png";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentType("image/png")
                        .contentLength(1024L)
                        .versionId("version-1")
                        .eTag("\"etag-1\"")
                        .build()
        );
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), pngSignature)
        );

        FileService.VerifiedJobCertificationImage result =
                service.verifyJobCertificationImage(userUuid, objectKey);

        assertThat(result.bucket()).isEqualTo("test-bucket");
        assertThat(result.objectKey()).isEqualTo(objectKey);
        assertThat(result.objectVersionId()).isEqualTo("version-1");
        assertThat(result.objectEtag()).isEqualTo("\"etag-1\"");
    }

    @Test
    void rejectsJobCertificationKeyOwnedByAnotherUser() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "job-certifications/" + UUID.randomUUID() + "/certificate.jpg";

        assertThatThrownBy(() -> service.verifyJobCertificationImage(userUuid, objectKey))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsImageWithSpoofedContentType() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "job-certifications/" + userUuid + "/fake.png";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/png").contentLength(512L)
                        .eTag("\"etag\"").build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "not an image".getBytes())
        );

        assertThatThrownBy(() -> service.verifyJobCertificationImage(userUuid, objectKey))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    @Test
    void rejectsImageWithoutStableSnapshotMetadata() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "job-certifications/" + userUuid + "/photo.png";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/png").contentLength(512L).build());

        assertThatThrownBy(() -> service.verifyJobCertificationImage(userUuid, objectKey))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.S3_CONNECTION_FAILED));
    }

    @Test
    void deletesKnownVersionAndCurrentJobCertificationImage() {
        UUID userUuid = UUID.randomUUID();
        String key = "job-certifications/" + userUuid + "/photo.jpg";

        service.deleteJobCertificationImage(userUuid,
                new FileService.VerifiedJobCertificationImage("test-bucket", key, "version-1", "\"etag\""));

        ArgumentCaptor<DeleteObjectRequest> requests = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(requests.capture());
        assertThat(requests.getAllValues()).extracting(DeleteObjectRequest::versionId)
                .containsExactly("version-1", null);
    }

    @Test
    void neverDeletesJobCertificationImageOutsideOwnersPrefix() {
        UUID userUuid = UUID.randomUUID();
        String foreignKey = "job-certifications/" + UUID.randomUUID() + "/photo.jpg";

        assertThatThrownBy(() -> service.deleteJobCertificationImage(userUuid,
                new FileService.VerifiedJobCertificationImage("test-bucket", foreignKey, null, null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        verifyNoInteractions(s3Client);
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

    @Test
    void verifiesUploadedFaceUpdateImage() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "face-images/" + userUuid + "/new-face.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/jpeg").contentLength(1024L).build());

        FileService.VerifiedS3Object result =
                service.verifyFaceUpdateMediaAndBuildFileUrl(userUuid, objectKey);

        assertThat(result.objectKey()).isEqualTo(objectKey);
    }

    @Test
    void createsPresignedUrlForFaceUpdateImage() throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(URI.create("https://upload.example.com/signed").toURL());
        UUID userUuid = UUID.randomUUID();

        PresignedUrlResDTO result = service.createPresignedUrl(
                userUuid, new PresignedUrlReqDTO("new-face.jpg", "image/jpeg", "face-images"));

        assertThat(result.objectKey()).startsWith("face-images/" + userUuid + "/");
    }

    @Test
    void acceptsExistingFaceVideoDirectoryForFaceUpdate() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "face-videos/" + userUuid + "/new-face.mp4";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("video/mp4").contentLength(1024L).build());

        assertThat(service.verifyFaceUpdateMediaAndBuildFileUrl(userUuid, objectKey).objectKey())
                .isEqualTo(objectKey);
    }

    @Test
    void rejectsFaceUpdateImageFromAnotherUser() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "face-images/" + UUID.randomUUID() + "/new-face.jpg";

        assertThatThrownBy(() -> service.verifyFaceUpdateMediaAndBuildFileUrl(userUuid, objectKey))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsUnsupportedFaceUpdateImageType() {
        UUID userUuid = UUID.randomUUID();
        String objectKey = "face-images/" + userUuid + "/new-face.gif";
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/gif").contentLength(1024L).build());

        assertThatThrownBy(() -> service.verifyFaceUpdateMediaAndBuildFileUrl(userUuid, objectKey))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
    }
}
