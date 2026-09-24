package com.mirrorsoul.mirrorsoul_api.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

class RagProfileConfigTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(RagProfileConfig.class, Scheduling.class);

    @Test
    void disabledFeatureNeedsNoAiSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("ragProfileRestClient");
            assertThat(context).doesNotHaveBean("ragProfileTaskScheduler");
        });
    }

    @Test
    void dedicatedSchedulerDoesNotReplaceDefaultScheduler() {
        runner.withPropertyValues("rag.profile.enabled=true", "rag.profile.base-url=http://ai.test",
                "clone-training.callback-secret=test-secret").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("ragProfileRestClient");
            assertThat(context).hasBean("ragProfileTaskScheduler");
            assertThat(context).hasBean("taskScheduler");
        });
    }
}
