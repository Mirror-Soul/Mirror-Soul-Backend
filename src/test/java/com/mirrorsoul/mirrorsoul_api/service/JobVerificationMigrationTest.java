package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JobVerificationMigrationTest {

    @Test
    void migrationAddsReviewHistoryWithoutConvertingExistingUsersToApproved() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:job_verification_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
        )) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(50), "
                    + "job_certification_object_key VARCHAR(500))");
            statement.execute("INSERT INTO users VALUES (1, 'nickname', 'job-certifications/old-photo.jpg')");
            statement.execute("INSERT INTO users VALUES (2, 'reviewer', NULL)");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V42__add_job_verification_requests.sql"
            ));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V44__remove_unverified_job_verification_name.sql"
            ));

            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM job_verification_requests")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            try (var rows = statement.executeQuery(
                    "SELECT job_certification_object_key FROM users WHERE id = 1"
            )) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("job-certifications/old-photo.jpg");
            }

            statement.execute("INSERT INTO job_verification_requests "
                    + "(user_id, claimed_job) VALUES "
                    + "(1, 'IT_TECH')");
            statement.execute("INSERT INTO job_verification_request_files "
                    + "(request_id, bucket, object_key, object_etag, display_order) VALUES "
                    + "(1, 'test-bucket', 'job-certifications/photo.jpg', '\"etag\"', 0)");

            try (var rows = statement.executeQuery(
                    "SELECT status FROM job_verification_requests WHERE id = 1"
            )) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("PENDING");
            }
            try (var rows = statement.executeQuery(
                    "SELECT object_etag FROM job_verification_request_files WHERE request_id = 1"
            )) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("\"etag\"");
            }

            assertThatThrownBy(() -> statement.execute(
                    "UPDATE job_verification_requests SET status = 'APPROVED' WHERE id = 1"
            )).isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.execute(
                    "UPDATE job_verification_requests SET status = 'REJECTED', "
                            + "reviewed_at = CURRENT_TIMESTAMP WHERE id = 1"
            )).isInstanceOf(SQLException.class);

            statement.execute("UPDATE job_verification_requests SET status = 'APPROVED', "
                    + "reviewer_user_id = 2, reviewed_at = CURRENT_TIMESTAMP WHERE id = 1");
            statement.execute("DELETE FROM users WHERE id = 2");

            try (var rows = statement.executeQuery(
                    "SELECT status, reviewer_user_id, reviewed_at "
                            + "FROM job_verification_requests WHERE id = 1"
            )) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("APPROVED");
                assertThat(rows.getObject("reviewer_user_id")).isNull();
                assertThat(rows.getTimestamp("reviewed_at")).isNotNull();
            }
        }
    }
}
