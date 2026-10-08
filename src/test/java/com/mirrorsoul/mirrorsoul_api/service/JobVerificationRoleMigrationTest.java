package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JobVerificationRoleMigrationTest {

    @Test
    void existingUsersDefaultToUserAndAdminMustBeExplicitlyGranted() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:job_verification_role;MODE=MySQL;DATABASE_TO_LOWER=TRUE")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO users VALUES (1)");
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V43__add_user_role.sql"));

            try (var rows = statement.executeQuery("SELECT account_role FROM users WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("USER");
            }
            statement.execute("UPDATE users SET account_role = 'ADMIN' WHERE id = 1");
            assertThatThrownBy(() -> statement.execute("UPDATE users SET account_role = 'INVALID' WHERE id = 1"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
