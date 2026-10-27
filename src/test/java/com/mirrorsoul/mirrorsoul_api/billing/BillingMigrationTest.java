package com.mirrorsoul.mirrorsoul_api.billing;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class BillingMigrationTest {
    @Test void migrationReconcilesLegacyBalancesAndProtectsFinancialHistory() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:billing_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")) {
            try (var s = c.createStatement()) {
                s.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, remaining_talk_time INT NOT NULL)");
                s.execute("CREATE TABLE video_calls(id BIGINT PRIMARY KEY, started_at DATETIME NOT NULL, status VARCHAR(20) NOT NULL)");
                s.execute("""
                        CREATE TABLE talk_time_transactions(id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
                        video_call_id BIGINT UNIQUE, reason VARCHAR(30) NOT NULL, delta_seconds INT NOT NULL,
                        balance_after_seconds INT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL)
                        """);
                s.execute("INSERT INTO users VALUES(1,120),(2,1800),(3,0)");
                s.execute("INSERT INTO talk_time_transactions(user_id,reason,delta_seconds,balance_after_seconds) VALUES(1,'OPENING_BALANCE',100,100)");
                s.execute("INSERT INTO video_calls VALUES(1,CURRENT_TIMESTAMP,'IN_PROGRESS')");
            }
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V51__payments_and_talk_time_billing.sql"));
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT user_id,SUM(delta_seconds) FROM talk_time_transactions GROUP BY user_id ORDER BY user_id")) {
                assertThat(r.next()).isTrue(); assertThat(r.getLong(1)).isEqualTo(1); assertThat(r.getInt(2)).isEqualTo(120);
                assertThat(r.next()).isTrue(); assertThat(r.getLong(1)).isEqualTo(2); assertThat(r.getInt(2)).isEqualTo(1800);
                assertThat(r.next()).isFalse();
            }
            try (var s = c.createStatement()) {
                String insert = """
                        INSERT INTO payments(store,identity_hash,reference_ciphertext,user_id,store_transaction_id,created_at,updated_at)
                        VALUES('APPLE','hash','ciphertext',1,'transaction',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """;
                s.execute(insert);
                assertThatThrownBy(() -> s.execute(insert)).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> s.execute("DELETE FROM users WHERE id=1")).isInstanceOf(SQLException.class);
                try (var r = s.executeQuery("SELECT connected_at FROM video_calls WHERE id=1")) {
                    assertThat(r.next()).isTrue(); assertThat(r.getTimestamp(1)).isNotNull();
                }
            }
        }
    }
}
