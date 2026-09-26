package com.mirrorsoul.mirrorsoul_api.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MeterFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "monitoring.cloudwatch", name = "enabled", havingValue = "true")
public class CloudWatchMetricsConfig {

    @Bean(destroyMethod = "close")
    public CloudWatchAsyncClient cloudWatchMetricsClient(
            @Value("${monitoring.cloudwatch.region}") String region) {
        // The default credential chain supports the EC2 instance role without embedded keys.
        return CloudWatchAsyncClient.builder().region(Region.of(region)).build();
    }

    @Bean(destroyMethod = "close")
    public CloudWatchMeterRegistry cloudWatchMeterRegistry(
            CloudWatchAsyncClient cloudWatchMetricsClient,
            @Value("${monitoring.cloudwatch.namespace}") String namespace,
            @Value("${monitoring.cloudwatch.step}") Duration step,
            @Value("${monitoring.cloudwatch.environment}") String environment,
            @Value("${monitoring.cloudwatch.instance}") String instance) {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public Duration step() {
                return step;
            }
        };
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(
                config, Clock.SYSTEM, cloudWatchMetricsClient);
        registry.config()
                .commonTags("application", "mirrorsoul-api", "environment", environment,
                        "instance", instance)
                .meterFilter(MeterFilter.deny(id -> !id.getName().startsWith("hikaricp.")));
        return registry;
    }
}
