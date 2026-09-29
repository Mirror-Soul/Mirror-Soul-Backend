package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class CloneSimilarityMigrationTest {
    @Test
    void keepsLegacyTotalAndDoesNotCopyItIntoVoiceScore() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:clone_similarity_migration;MODE=MySQL")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE clones (id BIGINT PRIMARY KEY, sync_rate INT NOT NULL DEFAULT 0)");
            statement.execute("INSERT INTO clones VALUES (1, 82)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V37__add_clone_similarity_scores.sql"));
            try (var rows = statement.executeQuery("SELECT * FROM clones WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("sync_rate")).isEqualByComparingTo("82.0");
                assertThat(rows.getBigDecimal("voice_similarity_score")).isNull();
                assertThat(rows.getBigDecimal("face_similarity_score")).isNull();
                assertThat(rows.getBigDecimal("profile_similarity_score")).isNull();
                assertThat(rows.getBigDecimal("data_reliability_score")).isNull();
                assertThat(rows.getString("similarity_score_version")).isNull();
                assertThat(rows.getBigDecimal("similarity_penalty")).isZero();
            }
            statement.execute("UPDATE clones SET sync_rate=94.7 WHERE id=1");
            try (var rows = statement.executeQuery("SELECT sync_rate FROM clones WHERE id=1")) {
                rows.next();
                assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("94.7");
                assertThat(rows.getBigDecimal(1).scale()).isEqualTo(1);
            }
        }
    }
}
