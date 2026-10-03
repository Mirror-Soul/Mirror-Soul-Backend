package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AiTalkLogMigrationTest {

    @Test
    void addsEventIdAndPreventsDuplicateEventWithinCall() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:ai_talk_log_migration;MODE=MySQL")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE talk_logs (
                        id BIGINT PRIMARY KEY,
                        video_call_id BIGINT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO talk_logs (id, video_call_id) VALUES (1, 10)");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V40__add_talk_log_event_id.sql"));

            statement.execute("""
                    INSERT INTO talk_logs (id, video_call_id, event_id)
                    VALUES (2, 10, '3a41085c-ce70-4c3c-b3a1-872105ac6512')
                    """);
            statement.execute("""
                    INSERT INTO talk_logs (id, video_call_id, event_id)
                    VALUES (3, 11, '3a41085c-ce70-4c3c-b3a1-872105ac6512')
                    """);

            try (var rows = statement.executeQuery(
                    "SELECT event_id FROM talk_logs WHERE id = 2")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1))
                        .isEqualTo("3a41085c-ce70-4c3c-b3a1-872105ac6512");
            }
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO talk_logs (id, video_call_id, event_id)
                    VALUES (4, 10, '3a41085c-ce70-4c3c-b3a1-872105ac6512')
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
