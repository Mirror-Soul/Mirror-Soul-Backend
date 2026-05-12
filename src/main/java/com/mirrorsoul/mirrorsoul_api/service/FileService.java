package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.PresignedUrlResDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.UploadCompleteReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.file.UploadCompleteResDTO;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final AwsS3Properties awsS3Properties;

    public PresignedUrlResDTO createPresignedUrl(PresignedUrlReqDTO request) {
        String directory = UploadDirectory.from(request.directory()).value();
        String sanitizedFileName = sanitizeFileName(request.fileName());
        String objectKey = directory + "/" + UUID.randomUUID() + "-" + sanitizedFileName;

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

    public UploadCompleteResDTO completeUpload(UploadCompleteReqDTO request) {
        UploadFileType fileType = UploadFileType.from(request.fileType());
        String objectKey = normalizeObjectKey(request.objectKey());

        if (!objectKey.startsWith(fileType.requiredPrefix())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "objectKey must start with " + fileType.requiredPrefix() + " for " + fileType.name() + "."
            );
        }

        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(awsS3Properties.getBucket())
                .key(objectKey)
                .build();

        try {
            s3Client.headObject(headObjectRequest);

            return new UploadCompleteResDTO(
                    fileType.name(),
                    objectKey,
                    buildFileUrl(objectKey),
                    true
            );
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
        FACE_VIDEOS("face-videos"),
        JOB_CERTIFICATIONS("job-certifications");

        private final String value;

        UploadDirectory(String value) {
            this.value = value;
        }

        public String value() {
            return value;
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
                    "directory must be one of: interviews, face-videos, job-certifications"
            );
        }
    }

    private enum UploadFileType {
        INTERVIEW_AUDIO("interviews/");

        private final String requiredPrefix;

        UploadFileType(String requiredPrefix) {
            this.requiredPrefix = requiredPrefix;
        }

        public String requiredPrefix() {
            return requiredPrefix;
        }

        public static UploadFileType from(String value) {
            if (value == null) {
                throw invalidFileType();
            }

            try {
                return UploadFileType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw invalidFileType();
            }
        }

        private static GeneralException invalidFileType() {
            return new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "fileType must be one of: INTERVIEW_AUDIO"
            );
        }
    }
}
