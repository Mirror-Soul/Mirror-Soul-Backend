package com.mirrorsoul.mirrorsoul_api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class ChangeHistoryMigrationTest {

    @Test
    void createsHistoryTablesAndCapturesExistingBalances() throws Exception {
        String databaseName = "history_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, remaining_talk_time INT NOT NULL)");
                statement.execute("CREATE TABLE video_calls (id BIGINT PRIMARY KEY)");
                statement.execute("CREATE TABLE talk_logs (id BIGINT PRIMARY KEY)");
                statement.execute("CREATE TABLE clones (id BIGINT PRIMARY KEY)");
                statement.execute("CREATE TABLE clone_profile_generation_jobs (id BIGINT PRIMARY KEY)");
                statement.execute("INSERT INTO users (id, remaining_talk_time) VALUES (1, 1800)");
            }

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V41__add_change_history_tables.sql"));

            try (Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery(
                        "SELECT reason, delta_seconds, balance_after_seconds FROM talk_time_transactions")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("reason")).isEqualTo("OPENING_BALANCE");
                    assertThat(result.getInt("delta_seconds")).isEqualTo(1800);
                    assertThat(result.getInt("balance_after_seconds")).isEqualTo(1800);
                    assertThat(result.next()).isFalse();
                }
                assertThat(columnExists(statement, "talk_logs", "revision_number")).isTrue();
                assertThat(tableExists(statement, "talk_log_revisions")).isTrue();
                assertThat(tableExists(statement, "clone_profile_versions")).isTrue();
                assertThat(columnExists(statement, "clone_profile_versions", "version_number")).isTrue();
            }
        }
    }

    private boolean tableExists(Statement statement, String table) throws Exception {
        try (ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + table + "'")) {
            result.next();
            return result.getInt(1) == 1;
        }
    }

    private boolean columnExists(Statement statement, String table, String column) throws Exception {
        try (ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            result.next();
            return result.getInt(1) == 1;
        }
    }
}
