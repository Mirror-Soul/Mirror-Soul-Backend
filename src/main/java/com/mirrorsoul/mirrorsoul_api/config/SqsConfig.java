package com.mirrorsoul.mirrorsoul_api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsSqsProperties.class)
public class SqsConfig {

    private final AwsS3Properties awsS3Properties;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsS3Properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
