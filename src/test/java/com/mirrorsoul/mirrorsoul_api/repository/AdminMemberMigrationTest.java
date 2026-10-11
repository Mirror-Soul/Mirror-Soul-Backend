package com.mirrorsoul.mirrorsoul_api.repository;
import java.nio.file.*;
import java.sql.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdminMemberMigrationTest {
    @Test void migrationCreatesTablesAndExtendsExistingRoles() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:member_migration;MODE=MySQL","sa","");
            var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, account_role VARCHAR(20) NOT NULL, "
                + "CONSTRAINT chk_users_account_role CHECK(account_role IN ('USER','ADMIN')))");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/main/resources/db/migration/V45__add_admin_member_management.sql"));
            var context=org.mockito.Mockito.mock(org.flywaydb.core.api.migration.Context.class);
            org.mockito.Mockito.when(context.getConnection()).thenReturn(connection);
            new db.migration.V46__extend_admin_roles().migrate(context);
            statement.execute("INSERT INTO users(id,account_role) VALUES (1,'SUPER_ADMIN')");
            try(var result=statement.executeQuery("SELECT version,session_version FROM users WHERE id=1")) {
                assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isZero();assertThat(result.getLong(2)).isZero();
            }
            assertThatThrownBy(() -> statement.execute("INSERT INTO users(id,account_role) VALUES (2,'INVALID')"))
                .isInstanceOf(SQLException.class);
        }
    }
}
