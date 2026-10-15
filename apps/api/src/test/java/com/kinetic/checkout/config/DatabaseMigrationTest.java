package com.kinetic.checkout.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kinetic.checkout.repository.PaymentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

class DatabaseMigrationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void upgradePreservesExistingOrdersAndBindsTheirOriginalPaymentKeys(int version) {
        var config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        var source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + directory.resolve("existing.sqlite"));
        new ResourceDatabasePopulator(
                        new ClassPathResource("db/schema.sql"),
                        new ClassPathResource("db/seed.sql"))
                .execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE payment_attempt_keys");
        if (version == 1) jdbc.execute("ALTER TABLE addresses DROP COLUMN active");
        jdbc.execute("PRAGMA user_version = " + version);
        jdbc.update(
                """
INSERT INTO orders VALUES (
    'KIN-existing','user_demo_1','checkout-key','addr_home','Demo Shopper',
    'demo@example.test','9000000000','12 Test Street','Bengaluru','Karnataka','560038',
    1299,29,1328,'INR','pending',1,'2026-10-15T00:00:00Z','2026-10-15T00:15:00Z',
    '2026-10-15T00:00:00Z')
""");
        jdbc.update(
                "INSERT INTO order_items VALUES ('KIN-existing','TRAINING-MAT','Training"
                    + " mat','mat',1299,1,1299)");
        jdbc.update("INSERT INTO payment_intents VALUES ('pi_existing','KIN-existing',1328,'INR')");
        jdbc.update(
                "INSERT INTO payment_attempts VALUES"
                    + " ('evt_existing','user_demo_1','KIN-existing','original-key','success','{}','2026-10-15T00:00:00Z')");
        jdbc.update("UPDATE products SET stock=7 WHERE sku='TRAINING-MAT'");
        var before = jdbc.queryForList("SELECT * FROM orders");

        DatabaseMigration.upgrade(source);
        DatabaseMigration.upgrade(source);

        assertThat(jdbc.queryForObject("PRAGMA user_version", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT * FROM orders")).isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT stock FROM products WHERE sku='TRAINING-MAT'",
                                Integer.class))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_intents", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt_keys", Integer.class))
                .isEqualTo(1);
        var repository = new PaymentRepository(jdbc);
        var attempt = repository.attemptByKey("user_demo_1", "original-key").orElseThrow();
        assertThat(attempt.eventId()).isEqualTo("evt_existing");
        repository.bindKey(attempt, "another-key");
        assertThat(repository.attemptByKey("user_demo_1", "another-key")).contains(attempt);
        assertThat(repository.attemptByKey("other-user", "another-key")).isEmpty();
        assertThat(jdbc.queryForList("PRAGMA foreign_key_check")).isEmpty();
    }

    @Test
    void bundledSampleUpgradesAutomaticallyOnFirstRun() throws Exception {
        var source =
                new DatabaseConfig()
                        .dataSource(
                                directory.resolve("fresh.sqlite").toString(),
                                "sample/checkout-sample.sqlite");
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("PRAGMA user_version", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM addresses", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt_keys", Integer.class))
                .isZero();
    }
}
