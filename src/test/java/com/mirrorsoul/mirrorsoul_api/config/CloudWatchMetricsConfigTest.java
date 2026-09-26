package com.mirrorsoul.mirrorsoul_api.config;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudWatchMetricsConfigTest {
    @Test
    void exportsOnlyPoolMetricsWithDeploymentDimensions() {
        CloudWatchAsyncClient client = mock(CloudWatchAsyncClient.class);
        when(client.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutMetricDataResponse.builder().build()));
        CloudWatchMeterRegistry registry = new CloudWatchMetricsConfig()
                .cloudWatchMeterRegistry(client, "MirrorSoul/Test", Duration.ofDays(1),
                        "test", "api-test");
        try {
            registry.stop();
            registry.counter("http.requests", "uri", "/users/123").increment();
            registry.counter("hikaricp.connections.timeout", "pool", "mysql").increment();

            assertThat(registry.find("http.requests").counter()).isNull();
            var meter = registry.get("hikaricp.connections.timeout").counter();
            assertThat(meter.getId().getTag("pool")).isEqualTo("mysql");
            assertThat(meter.getId().getTag("application")).isEqualTo("mirrorsoul-api");
            assertThat(meter.getId().getTag("environment")).isEqualTo("test");
            assertThat(meter.getId().getTag("instance")).isEqualTo("api-test");
        } finally {
            registry.close();
        }
    }
}
