package com.kinetic.checkout.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/** Preserves existing demo orders when upgrading the original local database. */
final class DatabaseMigration {
    private DatabaseMigration() {}

    static void upgrade(DataSource source) {
        var jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("PRAGMA user_version", Integer.class) >= 2) return;
        new TransactionTemplate(new DataSourceTransactionManager(source))
                .executeWithoutResult(
                        status -> {
                            jdbc.execute(
                                    "ALTER TABLE addresses ADD COLUMN active INTEGER NOT NULL"
                                            + " DEFAULT 1 CHECK (active IN (0,1))");
                            jdbc.execute(
                                    """
CREATE TABLE payment_attempts_v2 (
    event_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES profiles(id),
    order_id TEXT NOT NULL UNIQUE REFERENCES orders(id),
    retry_key TEXT NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN ('success','failure','pending','duplicate')),
    payload TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE (user_id, retry_key)
)
""");
                            jdbc.execute(
                                    "INSERT INTO payment_attempts_v2 SELECT * FROM"
                                            + " payment_attempts");
                            jdbc.execute("DROP TABLE payment_attempts");
                            jdbc.execute(
                                    "ALTER TABLE payment_attempts_v2 RENAME TO payment_attempts");
                            jdbc.execute("PRAGMA user_version = 2");
                        });
    }
}
