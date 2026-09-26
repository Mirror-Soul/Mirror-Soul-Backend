package com.mirrorsoul.mirrorsoul_api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "clone-profile.generation")
public class CloneProfileGenerationProperties {
    private int maxInterviews = 20;
    private int maxQuestionCharacters = 300;
    private int maxAnswerCharacters = 1000;
    private int maxTotalCharacters = 12000;
    private int maxAttempts = 3;
    private String interviewCompactionVersion = "recent-v1";
}
