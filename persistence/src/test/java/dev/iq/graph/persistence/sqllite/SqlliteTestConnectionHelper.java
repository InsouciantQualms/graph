/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.iq.common.fp.Io;
import java.sql.Connection;
import javax.sql.DataSource;

/**
 * Helper class for managing shared in-memory SQLite database connections for testing.
 */
final class SqlliteTestConnectionHelper {

    private static HikariDataSource dataSource;
    private static boolean schemaInitialized = false;
    private static final Object LOCK = new Object();

    static {
        // Register shutdown hook to ensure pool is closed
        Runtime.getRuntime().addShutdownHook(new Thread(SqlliteTestConnectionHelper::closeSharedDataSource));
    }

    private SqlliteTestConnectionHelper() {}

    /**
     * Gets or creates a shared DataSource for testing with an in-memory SQLite database.
     */
    static DataSource getSharedDataSource() {

        synchronized (LOCK) {
            if ((dataSource == null) || dataSource.isClosed()) {
                createDataSource();
            }
            ensureSchemaInitialized();
            return dataSource;
        }
    }

    /**
     * Closes the shared DataSource if it exists.
     */
    static void closeSharedDataSource() {

        synchronized (LOCK) {
            if ((dataSource != null) && !dataSource.isClosed()) {
                dataSource.close();
                dataSource = null;
                schemaInitialized = false;
            }
        }
    }

    /**
     * Resets the schema initialization flag for testing.
     */
    static void resetSchemaInitialized() {

        synchronized (LOCK) {
            schemaInitialized = false;
        }
    }

    private static void createDataSource() {

        final var config = new HikariConfig();
        // Use shared in-memory database that persists between connections
        config.setJdbcUrl("jdbc:sqlite:file:testdb?mode=memory&cache=shared");
        config.setDriverClassName("org.sqlite.JDBC");
        // Maintain one idle connection to keep the in-memory database alive
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(5);
        config.setIdleTimeout(30000); // 30 seconds
        config.setMaxLifetime(60000); // 1 minute
        config.setConnectionTimeout(5000); // 5 seconds
        config.setLeakDetectionThreshold(10000); // 10 seconds
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("SqliteTestPool");
        // Allow pool to be closed even with active connections
        config.setAllowPoolSuspension(true);

        dataSource = new HikariDataSource(config);
    }

    private static void ensureSchemaInitialized() {

        if (!schemaInitialized) {
            Io.withVoid(() -> {
                try (var connection = dataSource.getConnection()) {
                    createSchema(connection);
                    schemaInitialized = true;
                }
            });
        }
    }

    private static void createSchema(final Connection connection) throws Exception {

        try (var statement = connection.createStatement()) {
            // Enable foreign keys for SQLite
            statement.execute("PRAGMA foreign_keys = ON");

            // Create node table
            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS node (
                                id TEXT NOT NULL,
                                version_id INTEGER NOT NULL,
                                type TEXT NOT NULL,
                                created TEXT NOT NULL,
                                expired TEXT,
                                PRIMARY KEY (id, version_id)
                            )
                            """);

            // Create edge table
            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS edge (
                                id TEXT NOT NULL,
                                version_id INTEGER NOT NULL,
                                type TEXT NOT NULL,
                                source_id TEXT NOT NULL,
                                source_version_id INTEGER NOT NULL,
                                target_id TEXT NOT NULL,
                                target_version_id INTEGER NOT NULL,
                                created TEXT NOT NULL,
                                expired TEXT,
                                PRIMARY KEY (id, version_id),
                                FOREIGN KEY (source_id, source_version_id) REFERENCES node(id, version_id),
                                FOREIGN KEY (target_id, target_version_id) REFERENCES node(id, version_id)
                            )
                            """);

            // Create component table
            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS component (
                                id TEXT NOT NULL,
                                version_id INTEGER NOT NULL,
                                created TEXT NOT NULL,
                                expired TEXT,
                                PRIMARY KEY (id, version_id)
                            )
                            """);

            // Create component_nodes table for many-to-many relationship
            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS component_nodes (
                                component_id TEXT NOT NULL,
                                component_version INTEGER NOT NULL,
                                node_id TEXT NOT NULL,
                                node_version INTEGER NOT NULL,
                                PRIMARY KEY (component_id, component_version, node_id, node_version),
                                FOREIGN KEY (component_id, component_version) REFERENCES component(id, version_id),
                                FOREIGN KEY (node_id, node_version) REFERENCES node(id, version_id)
                            )
                            """);

            // Create component_edges table for many-to-many relationship
            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS component_edges (
                                component_id TEXT NOT NULL,
                                component_version INTEGER NOT NULL,
                                edge_id TEXT NOT NULL,
                                edge_version INTEGER NOT NULL,
                                PRIMARY KEY (component_id, component_version, edge_id, edge_version),
                                FOREIGN KEY (component_id, component_version) REFERENCES component(id, version_id),
                                FOREIGN KEY (edge_id, edge_version) REFERENCES edge(id, version_id)
                            )
                            """);

            // Create properties tables for storing node/edge/component data
            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS node_properties (
                                id TEXT NOT NULL,
                                version_id INTEGER NOT NULL,
                                property_key TEXT NOT NULL,
                                property_value TEXT NOT NULL,
                                PRIMARY KEY (id, version_id, property_key),
                                FOREIGN KEY (id, version_id) REFERENCES node(id, version_id)
                            )
                            """);

            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS edge_properties (
                                id TEXT NOT NULL,
                                version_id INTEGER NOT NULL,
                                property_key TEXT NOT NULL,
                                property_value TEXT NOT NULL,
                                PRIMARY KEY (id, version_id, property_key),
                                FOREIGN KEY (id, version_id) REFERENCES edge(id, version_id)
                            )
                            """);

            statement.execute(
                    """
                            CREATE TABLE IF NOT EXISTS component_properties (
                                id TEXT NOT NULL,
                                version_id INTEGER NOT NULL,
                                property_key TEXT NOT NULL,
                                property_value TEXT NOT NULL,
                                PRIMARY KEY (id, version_id, property_key),
                                FOREIGN KEY (id, version_id) REFERENCES component(id, version_id)
                            )
                            """);

            // Create indexes for better query performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_node_expired ON node(id, expired)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_edge_expired ON edge(id, expired)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_component_expired ON component(id, expired)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_edge_source ON edge(source_id, source_version_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_edge_target ON edge(target_id, target_version_id)");
        }
    }
}
