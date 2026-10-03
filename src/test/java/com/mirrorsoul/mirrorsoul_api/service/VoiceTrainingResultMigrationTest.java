package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class VoiceTrainingResultMigrationTest {
    @Test
    void addsFailureFieldsAndPreventsDuplicateProfilesForOneJob() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:voice_result_migration;MODE=MySQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE voice_training_jobs (id BIGINT PRIMARY KEY, error_message TEXT)");
            statement.execute("CREATE TABLE ai_voice_profiles (id BIGINT PRIMARY KEY, voice_training_job_id BIGINT NOT NULL)");
            statement.execute("INSERT INTO voice_training_jobs VALUES (1, NULL)");
            statement.execute("INSERT INTO ai_voice_profiles VALUES (1, 1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V39__voice_result_persistence.sql"));
            statement.execute("UPDATE voice_training_jobs SET error_code='MODEL_ERROR', error_retryable=TRUE WHERE id=1");
            try (var rows = statement.executeQuery("SELECT error_code, error_retryable FROM voice_training_jobs WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("MODEL_ERROR");
                assertThat(rows.getBoolean(2)).isTrue();
            }
            assertThatThrownBy(() -> statement.execute("INSERT INTO ai_voice_profiles VALUES (2, 1)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
