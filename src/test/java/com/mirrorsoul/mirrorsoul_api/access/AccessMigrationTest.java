package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
class AccessMigrationTest {
 @Test void createsStaffPermissionsAndAuditTables() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:accessmigration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","")) {
   c.createStatement().execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V49__add_admin_access_security.sql"));
   c.createStatement().execute("INSERT INTO users VALUES(1)");c.createStatement().execute("INSERT INTO admin_staff(user_id,display_name,state,updated_at) VALUES(1,'staff','ACTIVE',CURRENT_TIMESTAMP)");
   c.createStatement().execute("INSERT INTO admin_staff_permissions VALUES(1,'member_read')");assertThatThrownBy(()->c.createStatement().execute("INSERT INTO admin_staff_permissions VALUES(1,'member_read')")).isInstanceOf(java.sql.SQLException.class);
  }
 }
}
