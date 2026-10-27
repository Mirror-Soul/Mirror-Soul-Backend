package db.migration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
public class V46__extend_admin_roles extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        String database=context.getConnection().getMetaData().getDatabaseProductName();
        String clause=database.equals("MySQL") ? "DROP CHECK" : "DROP CONSTRAINT";
        try (var statement=context.getConnection().createStatement()) {
            if (database.equals("MySQL")) {
                statement.execute("ALTER TABLE member_action_history MODIFY idempotency_key "
                        + "VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
            }
            statement.execute("ALTER TABLE users " + clause + " chk_users_account_role");
            statement.execute("ALTER TABLE users ADD CONSTRAINT chk_users_account_role "
                + "CHECK (account_role IN ('USER','ADMIN','ADMIN_VIEWER','SUPER_ADMIN'))");
        }
    }
}
