package com.mirrorsoul.mirrorsoul_api.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws.s3")
public class AwsS3Properties {

    @NotBlank
    private String bucket;

    @NotBlank
    private String region;

    @Min(1)
    private long presignedUrlExpirationMinutes = 5;
}
