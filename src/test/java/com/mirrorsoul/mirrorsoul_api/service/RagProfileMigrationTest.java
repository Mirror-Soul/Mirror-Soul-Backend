package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class RagProfileMigrationTest {
    @Test
    void migrationSupportsPendingRevisionAndUniqueClone() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:rag_migration;MODE=MySQL");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE clones (id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO clones VALUES (14)");
            sql.execute(Files.readString(Path.of("src/main/resources/db/migration/V35__add_rag_profile_jobs.sql")));
            sql.execute("INSERT INTO rag_profile_jobs (clone_id, next_attempt_at) VALUES (14, CURRENT_TIMESTAMP)");
            try (var rows = sql.executeQuery("SELECT requested_revision, delivered_revision, attempts FROM rag_profile_jobs")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
                assertThat(rows.getLong(2)).isZero();
                assertThat(rows.getInt(3)).isZero();
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO rag_profile_jobs (clone_id, next_attempt_at) VALUES (14, CURRENT_TIMESTAMP)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
