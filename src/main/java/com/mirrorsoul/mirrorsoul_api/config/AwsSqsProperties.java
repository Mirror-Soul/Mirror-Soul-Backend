package com.mirrorsoul.mirrorsoul_api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws.sqs")
public class AwsSqsProperties {

    @NotBlank
    private String voiceTrainingQueueUrl;
}
