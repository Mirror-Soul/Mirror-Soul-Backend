package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class FaceTrainingResultMigrationTest {
    @Test
    void migrationPreservesExistingRowsAndDefaultsReadinessToPending() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:face_migration;MODE=MySQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE face_training_jobs (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE ai_face_profiles (id BIGINT PRIMARY KEY, avatar_cache_object_key VARCHAR(500))");
            statement.execute("CREATE TABLE clones (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO clones VALUES (1)");
            statement.execute("INSERT INTO ai_face_profiles VALUES (1, 'existing-cache')");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V33__add_face_result_artifacts_and_clone_readiness.sql"));
            try (var rows = statement.executeQuery("SELECT status, personality_training_completed FROM clones WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("PENDING");
                assertThat(rows.getBoolean(2)).isFalse();
            }
            try (var rows = statement.executeQuery("SELECT avatar_cache_object_key, profile_key FROM ai_face_profiles WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("existing-cache");
                assertThat(rows.getString(2)).isNull();
            }
        }
    }
}
