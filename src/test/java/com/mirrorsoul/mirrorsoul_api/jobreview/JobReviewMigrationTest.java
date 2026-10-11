package com.mirrorsoul.mirrorsoul_api.jobreview;
import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
class JobReviewMigrationTest {
 @Test void existingRowsGetRetentionWithoutResendingHistoricalDecisions() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:job_review_extension_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE")) {
   var s=c.createStatement();
   s.execute("CREATE TABLE job_verification_requests(id BIGINT PRIMARY KEY, status VARCHAR(20),created_at DATETIME, reviewed_at DATETIME)");
   s.execute("INSERT INTO job_verification_requests VALUES(1,'PENDING','2026-01-01 00:00:00',NULL),(2,'APPROVED','2026-01-01 00:00:00','2026-01-02 00:00:00')");
   ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V50__job_review_lifecycle.sql"));
   var rows=s.executeQuery("SELECT * FROM job_verification_requests ORDER BY id");
   assertThat(rows.next()).isTrue();assertThat(rows.getTimestamp("evidence_expires_at").toLocalDateTime()).isEqualTo(java.time.LocalDateTime.parse("2026-04-01T00:00:00"));
   assertThat(rows.next()).isTrue();assertThat(rows.getTimestamp("evidence_expires_at").toLocalDateTime()).isEqualTo(java.time.LocalDateTime.parse("2026-02-01T00:00:00"));
   assertThat(rows.getString("notification_state")).isEqualTo("NONE");assertThat(rows.getLong("version")).isZero();
  }
 }
}
