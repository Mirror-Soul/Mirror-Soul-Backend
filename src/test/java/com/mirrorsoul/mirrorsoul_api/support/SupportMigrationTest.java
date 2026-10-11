package com.mirrorsoul.mirrorsoul_api.support;
import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
class SupportMigrationTest {
 @Test void migrationCreatesTablesAndEnforcesIdempotentKeys() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:supportmigration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","")) {
   c.createStatement().execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
   ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V48__create_support_tickets.sql"));
   c.createStatement().execute("INSERT INTO users VALUES(1)");
   String insert="INSERT INTO support_tickets(member_id,channel,category,status,subject,created_at,updated_at,request_key,request_hash) VALUES(1,'APP','OTHER','RECEIVED','subject',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'key','hash')";
   c.createStatement().execute(insert);assertThatThrownBy(()->c.createStatement().execute(insert)).isInstanceOf(java.sql.SQLException.class);
   try(var r=c.createStatement().executeQuery("SELECT COUNT(*) FROM support_entries")) {assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isZero();}
  }
 }
}
