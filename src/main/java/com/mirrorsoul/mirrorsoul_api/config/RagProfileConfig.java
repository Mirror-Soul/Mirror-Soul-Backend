package com.mirrorsoul.mirrorsoul_api.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "rag.profile", name = "enabled", havingValue = "true")
public class RagProfileConfig {
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler ragProfileTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("rag-profile-");
        return scheduler;
    }

    @Bean(defaultCandidate = false)
    public RestClient ragProfileRestClient(
            @Value("${rag.profile.base-url:}") String baseUrl,
            @Value("${clone-training.callback-secret:}") String callbackSecret) {
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("AI_SERVER_BASE_URL must be an HTTP(S) server URL");
        }
        if (callbackSecret.isBlank()) {
            throw new IllegalStateException("CLONE_TRAINING_CALLBACK_SECRET is required for RAG profiles");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
