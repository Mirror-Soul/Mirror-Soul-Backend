package com.mirrorsoul.mirrorsoul_api.safety;
import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
class SafetyMigrationTest {
    @Test void migrationAddsRestrictedDeadlineAndAllSafetyTables() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:safety_migration;MODE=MySQL","sa","");
            var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/main/resources/db/migration/V47__add_report_safety.sql"));
            statement.execute("INSERT INTO users(id) VALUES (1)");
            try(var result=statement.executeQuery("SELECT safety_restricted_until FROM users WHERE id=1")) {
                assertThat(result.next()).isTrue();assertThat(result.getTimestamp(1)).isNull();
            }
            for(String table:new String[]{"safety_reports","safety_decisions","safety_sanctions","safety_appeals","safety_notices","safety_evidence_audits","safety_call_diagnostics"}) {
                try(var result=statement.executeQuery("SELECT count(*) FROM "+table)) {
                    assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isZero();
                }
            }
        }
    }
}
