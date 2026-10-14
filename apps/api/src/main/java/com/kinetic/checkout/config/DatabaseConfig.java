package com.kinetic.checkout.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    @Bean
    DataSource dataSource(
            @Value("${app.database}") String database,
            @Value("${app.sample-database}") String sample)
            throws IOException {
        Path path = Path.of(database).toAbsolutePath();
        Files.createDirectories(path.getParent());
        boolean fresh = Files.notExists(path);
        boolean copiedSample = fresh && Files.exists(Path.of(sample));
        if (copiedSample) {
            Files.copy(Path.of(sample), path);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        // Acquire the writer lock before a read-then-write business transaction.
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + path);
        if (fresh && !copiedSample) {
            new ResourceDatabasePopulator(
                            new ClassPathResource("db/schema.sql"),
                            new ClassPathResource("db/seed.sql"))
                    .execute(source);
        }
        DatabaseMigration.upgrade(source);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
