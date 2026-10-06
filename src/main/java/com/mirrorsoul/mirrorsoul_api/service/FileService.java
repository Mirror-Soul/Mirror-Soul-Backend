package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlResDTO;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final AwsS3Properties awsS3Properties;

    public PresignedUrlResDTO createPresignedUrl(UUID userUuid, PresignedUrlReqDTO request) {
        UploadDirectory uploadDirectory = UploadDirectory.from(request.directory());
        uploadDirectory.validateContentType(request.contentType());
        String directory = uploadDirectory.value();
        String sanitizedFileName = sanitizeFileName(request.fileName());
        String objectKey = directory + "/" + userUuid + "/" + UUID.randomUUID() + "-" + sanitizedFileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsS3Properties.getBucket())
                .key(objectKey)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(awsS3Properties.getPresignedUrlExpirationMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();

        try {
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            return new PresignedUrlResDTO(
                    presignedRequest.url().toString(),
                    buildFileUrl(objectKey),
                    objectKey
            );
        } catch (AwsServiceException | SdkClientException e) {
            throw new GeneralException(
                    GeneralErrorCode.S3_CONNECTION_FAILED,
                    "Failed to generate presigned URL."
            );
        }
    }

    public String createPresignedDownloadUrl(String bucket, String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(normalizedObjectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(awsS3Properties.getPresignedUrlExpirationMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (AwsServiceException | SdkClientException e) {
            throw new GeneralException(
                    GeneralErrorCode.S3_CONNECTION_FAILED,
                    "Failed to generate download URL."
            );
        }
    }

    public String createPresignedDownloadUrlOrFallback(String objectKey, String fallbackUrl) {
        if (objectKey == null || objectKey.isBlank()) {
            return fallbackUrl;
        }
        return createPresignedDownloadUrl(awsS3Properties.getBucket(), objectKey);
    }

    public VerifiedS3Object verifyInterviewAudioAndBuildFileUrl(UUID userUuid, String objectKey) {
        return verifyUploadedObjectAndBuildFileUrl(userUuid, objectKey, UploadFileType.INTERVIEW_AUDIO);
    }

    public VerifiedS3Object verifyVoiceUpdateAudioAndBuildFileUrl(UUID userUuid, String objectKey) {
        return verifyUploadedObjectAndBuildFileUrl(userUuid, objectKey, UploadFileType.VOICE_UPDATE_AUDIO);
    }

    public VerifiedS3Object verifyFaceVideoAndBuildFileUrl(UUID userUuid, String objectKey) {
        return verifyUploadedObjectAndBuildFileUrl(userUuid, objectKey, UploadFileType.FACE_VIDEO);
    }

    public VerifiedS3Object verifyFaceUpdateMediaAndBuildFileUrl(UUID userUuid, String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        UploadFileType type = normalizedObjectKey.startsWith(UploadFileType.FACE_IMAGE.requiredPrefix(userUuid))
                ? UploadFileType.FACE_IMAGE
                : UploadFileType.FACE_VIDEO;
        return verifyUploadedObjectAndBuildFileUrl(userUuid, normalizedObjectKey, type);
    }

    public VerifiedS3Object verifyProfileImageAndBuildFileUrl(UUID userUuid, String objectKey) {
        return verifyUploadedObjectAndBuildFileUrl(userUuid, objectKey, UploadFileType.PROFILE_IMAGE);
    }

    private VerifiedS3Object verifyUploadedObjectAndBuildFileUrl(
            UUID userUuid,
            String objectKey,
            UploadFileType uploadFileType
    ) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        String requiredPrefix = uploadFileType.requiredPrefix(userUuid);

        if (!normalizedObjectKey.startsWith(requiredPrefix)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "objectKey must start with " + requiredPrefix
                            + " for " + uploadFileType.name() + "."
            );
        }

        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(awsS3Properties.getBucket())
                .key(normalizedObjectKey)
                .build();

        try {
            HeadObjectResponse objectMetadata = s3Client.headObject(headObjectRequest);
            uploadFileType.validateMetadata(objectMetadata);

            return new VerifiedS3Object(buildFileUrl(normalizedObjectKey), normalizedObjectKey);
        } catch (AwsServiceException e) {
            if (isNotFound(e)) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "Uploaded S3 object was not found."
                );
            }

            throw new GeneralException(
                    GeneralErrorCode.S3_CONNECTION_FAILED,
                    "Failed to verify uploaded S3 object."
            );
        } catch (SdkClientException e) {
            throw new GeneralException(
                    GeneralErrorCode.S3_CONNECTION_FAILED,
                    "Failed to verify uploaded S3 object."
            );
        }
    }

    private String sanitizeFileName(String fileName) {
        String baseName = fileName.replace("\\", "/");
        int lastSlashIndex = baseName.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            baseName = baseName.substring(lastSlashIndex + 1);
        }

        String sanitized = baseName
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^A-Za-z0-9._-]", "");

        return sanitized.isBlank() ? "file" : sanitized;
    }

    private String buildFileUrl(String objectKey) {
        return "https://" + awsS3Properties.getBucket()
                + ".s3."
                + awsS3Properties.getRegion()
                + ".amazonaws.com/"
                + objectKey;
    }

    private String normalizeObjectKey(String objectKey) {
        String normalized = objectKey == null ? "" : objectKey.trim().replace("\\", "/");

        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "Invalid objectKey."
            );
        }

        return normalized;
    }

    private boolean isNotFound(AwsServiceException e) {
        AwsErrorDetails errorDetails = e.awsErrorDetails();
        return e.statusCode() == 404
                || (errorDetails != null && "NoSuchKey".equals(errorDetails.errorCode()));
    }

    private enum UploadDirectory {
        INTERVIEWS("interviews"),
        VOICE_UPDATES("voice-updates"),
        FACE_VIDEOS("face-videos"),
        FACE_IMAGES("face-images"),
        JOB_CERTIFICATIONS("job-certifications"),
        PROFILE_IMAGES("profile-images");

        private final String value;

        UploadDirectory(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public void validateContentType(String contentType) {
            if (this != PROFILE_IMAGES && this != FACE_IMAGES) {
                return;
            }

            boolean supportedType = "image/jpeg".equalsIgnoreCase(contentType)
                    || "image/png".equalsIgnoreCase(contentType)
                    || "image/webp".equalsIgnoreCase(contentType);
            if (!supportedType) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "Image contentType must be one of: image/jpeg, image/png, image/webp."
                );
            }
        }

        public static UploadDirectory from(String value) {
            if (value == null) {
                throw invalidDirectory();
            }

            for (UploadDirectory directory : values()) {
                if (directory.value.equals(value.toLowerCase(Locale.ROOT))) {
                    return directory;
                }
            }

            throw invalidDirectory();
        }

        private static GeneralException invalidDirectory() {
            return new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "directory must be one of: interviews, voice-updates, face-videos, face-images, "
                            + "job-certifications, profile-images"
            );
        }
    }

    private enum UploadFileType {
        INTERVIEW_AUDIO("interviews"),
        VOICE_UPDATE_AUDIO("voice-updates"),
        FACE_VIDEO("face-videos"),
        FACE_IMAGE("face-images"),
        PROFILE_IMAGE("profile-images");

        private static final long MAX_FACE_VIDEO_SIZE_BYTES = 100L * 1024 * 1024;
        private static final long MAX_PROFILE_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;

        private final String requiredPrefix;

        UploadFileType(String requiredPrefix) {
            this.requiredPrefix = requiredPrefix;
        }

        public String requiredPrefix(UUID userUuid) {
            return requiredPrefix + "/" + userUuid + "/";
        }

        public void validateMetadata(HeadObjectResponse metadata) {
            if (this == FACE_VIDEO) {
                validateFaceVideo(metadata);
            } else if (this == PROFILE_IMAGE || this == FACE_IMAGE) {
                validateImage(metadata);
            }
        }

        private void validateFaceVideo(HeadObjectResponse metadata) {
            String contentType = metadata.contentType();
            boolean supportedType = "video/mp4".equalsIgnoreCase(contentType)
                    || "video/quicktime".equalsIgnoreCase(contentType)
                    || "video/webm".equalsIgnoreCase(contentType);
            if (!supportedType) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "Face video contentType must be one of: video/mp4, video/quicktime, video/webm."
                );
            }

            if (metadata.contentLength() == null
                    || metadata.contentLength() <= 0
                    || metadata.contentLength() > MAX_FACE_VIDEO_SIZE_BYTES) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "Face video size must be greater than 0 and at most 100 MB."
                );
            }
        }

        private void validateImage(HeadObjectResponse metadata) {
            String imageType = this == FACE_IMAGE ? "Face image" : "Profile image";
            String contentType = metadata.contentType();
            boolean supportedType = "image/jpeg".equalsIgnoreCase(contentType)
                    || "image/png".equalsIgnoreCase(contentType)
                    || "image/webp".equalsIgnoreCase(contentType);
            if (!supportedType) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        imageType + " contentType must be one of: image/jpeg, image/png, image/webp."
                );
            }

            if (metadata.contentLength() == null
                    || metadata.contentLength() <= 0
                    || metadata.contentLength() > MAX_PROFILE_IMAGE_SIZE_BYTES) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        imageType + " size must be greater than 0 and at most 5 MB."
                );
            }
        }
    }

    public record VerifiedS3Object(String fileUrl, String objectKey) {
    }
}
