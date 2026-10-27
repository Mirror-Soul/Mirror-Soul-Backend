package com.mirrorsoul.mirrorsoul_api.quality;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class QualityMigrationTest {
    @Test void migrationPreservesHistoricalRowsAndEnforcesEventAndMeasurementUniqueness() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:quality_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE")){
            try(var s=c.createStatement()){
                s.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE clones(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE video_calls(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE voice_training_jobs(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE face_training_jobs(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE clone_profile_generation_jobs(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE rag_profile_jobs(clone_id BIGINT PRIMARY KEY,next_attempt_at DATETIME(3))");
                s.execute("INSERT INTO users VALUES(1)");s.execute("INSERT INTO clones VALUES(1)");
                s.execute("INSERT INTO video_calls VALUES(1)");s.execute("INSERT INTO voice_training_jobs VALUES(1)");
                s.execute("INSERT INTO rag_profile_jobs VALUES(1,CURRENT_TIMESTAMP)");
            }
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V52__twin_training_and_call_diagnostics.sql"));
            try(var s=c.createStatement()){
                try(var r=s.executeQuery("SELECT clone_version,correlation_id FROM video_calls WHERE id=1")){
                    assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isZero();assertThat(r.getString(2)).isNull();
                }
                try(var r=s.executeQuery("SELECT created_at,updated_at,retry_count FROM rag_profile_jobs WHERE clone_id=1")){
                    assertThat(r.next()).isTrue();assertThat(r.getTimestamp(1)).isNotNull();
                    assertThat(r.getTimestamp(2)).isEqualTo(r.getTimestamp(1));assertThat(r.getInt(3)).isZero();
                }
                String event="""
                    INSERT INTO call_quality_events(call_id,event_key,request_hash,turn_key,step,occurred_at,created_at)
                    VALUES(1,'event','request','turn','STT_ENDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """;
                s.execute(event);assertThatThrownBy(()->s.execute(event)).isInstanceOf(SQLException.class);
                String sample="""
                    INSERT INTO call_stage_measurements(call_id,turn_key,stage,latency_ms,measured_at)
                    VALUES(1,'turn','STT_TO_LLM',10,CURRENT_TIMESTAMP)
                    """;
                s.execute(sample);assertThatThrownBy(()->s.execute(sample)).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.execute(sample.replace("'turn'","'negative'").replace(",10,",",-1,")))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.execute("DELETE FROM video_calls WHERE id=1")).isInstanceOf(SQLException.class);
            }
        }
    }
}
