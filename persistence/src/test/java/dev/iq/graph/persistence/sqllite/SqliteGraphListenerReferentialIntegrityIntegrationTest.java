/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;

/**
 * Integration tests for SqliteGraphRepository to validate
 * referential integrity when using graph listeners.
 */
@DisplayName("SQLite GraphSpace Listener Referential Integrity Integration Tests")
final class SqliteGraphListenerReferentialIntegrityIntegrationTest
        extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    private DataSource dataSource;

    private static DataSource createInMemoryDataSource() {
        final var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // Use single connection to avoid locking issues
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Override
    protected GraphRepository createGraphRepository() {
        // Initialize dataSource if not already done
        if (dataSource == null) {
            dataSource = createInMemoryDataSource();
        }

        // Create a new repository with its own handle managed by the session
        final var sessionFactory = new SqliteSessionFactory(dataSource);
        final var session = sessionFactory.create();
        return SqliteGraphRepository.create((SqliteHandleProvider) session);
    }
}
