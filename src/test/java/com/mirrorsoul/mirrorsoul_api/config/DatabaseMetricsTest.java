package com.mirrorsoul.mirrorsoul_api.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseMetricsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                    SimpleMetricsExportAutoConfiguration.class,
                    DataSourcePoolMetricsAutoConfiguration.class))
            .withUserConfiguration(CloudWatchMetricsConfig.class);

    @Test
    void localStartupDoesNotCreateAwsClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("cloudWatchMetricsClient");
            assertThat(context).doesNotHaveBean("cloudWatchMeterRegistry");
        });
    }

    @Test
    void autoConfigurationSeparatesPoolsAndRecordsAcquisitionTimeout() {
        runner.withBean("dataSource", HikariDataSource.class, () -> pool("mysql"))
                .withBean("vectorDataSource", HikariDataSource.class, () -> pool("postgresql"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    HikariDataSource mysql = context.getBean("dataSource", HikariDataSource.class);
                    HikariDataSource postgres = context.getBean("vectorDataSource", HikariDataSource.class);
                    try (Connection mysqlConnection = mysql.getConnection();
                         Connection postgresConnection = postgres.getConnection()) {
                        for (String name : new String[]{"mysql", "postgresql"}) {
                            assertThat(registry.get("hikaricp.connections.max")
                                    .tag("pool", name).gauge().value()).isEqualTo(1);
                            assertThat(registry.get("hikaricp.connections.acquire")
                                    .tag("pool", name).timer().count()).isPositive();
                        }
                        assertThrows(SQLException.class, mysql::getConnection);
                        assertThat(registry.get("hikaricp.connections.timeout")
                                .tag("pool", "mysql").counter().count()).isEqualTo(1);
                        assertThat(registry.get("hikaricp.connections.timeout")
                                .tag("pool", "postgresql").counter().count()).isZero();
                    }
                });
    }

    private static HikariDataSource pool(String name) {
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl("jdbc:h2:mem:metrics_" + name);
        source.setPoolName(name);
        source.setMaximumPoolSize(1);
        source.setMinimumIdle(0);
        source.setConnectionTimeout(250);
        return source;
    }
}
