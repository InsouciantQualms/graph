/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


package dev.iq.graph.persistence.sqllite;

import dev.iq.common.fp.Io;
import dev.iq.common.persist.Session;
import dev.iq.common.persist.SessionFactory;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import javax.sql.DataSource;

/**
 * SQLite implementation of SessionFactory.
 */
public final class SqliteSessionFactory implements SessionFactory {

    private final Jdbi jdbi;

    public SqliteSessionFactory(final DataSource dataSource) {

        this.jdbi = createJdbi(dataSource);
        initializeSchema();
    }

    private Jdbi createJdbi(final DataSource dataSource) {

        final var jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
        return jdbi;
    }

    private void initializeSchema() {

        Io.withVoid(() -> jdbi.useHandle(handle -> {
            // Enable foreign keys for SQLite
            handle.execute("PRAGMA foreign_keys = ON");

            // Create node table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS node (
                    id TEXT NOT NULL,
                    version_id INTEGER NOT NULL,
                    created TEXT NOT NULL,
                    expired TEXT,
                    PRIMARY KEY (id, version_id)
                )
                """);

            // Create node properties table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS node_properties (
                    id TEXT NOT NULL,
                    version_id INTEGER NOT NULL,
                    property_key TEXT NOT NULL,
                    property_value TEXT NOT NULL,
                    PRIMARY KEY (id, version_id, property_key),
                    FOREIGN KEY (id, version_id) REFERENCES node(id, version_id)
                )
                """);

            // Create edge table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS edge (
                    id TEXT NOT NULL,
                    version_id INTEGER NOT NULL,
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

            // Create edge properties table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS edge_properties (
                    id TEXT NOT NULL,
                    version_id INTEGER NOT NULL,
                    property_key TEXT NOT NULL,
                    property_value TEXT NOT NULL,
                    PRIMARY KEY (id, version_id, property_key),
                    FOREIGN KEY (id, version_id) REFERENCES edge(id, version_id)
                )
                """);

            // Create component table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS component (
                    id TEXT NOT NULL,
                    version_id INTEGER NOT NULL,
                    created TEXT NOT NULL,
                    expired TEXT,
                    PRIMARY KEY (id, version_id)
                )
                """);

            // Create component properties table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS component_properties (
                    id TEXT NOT NULL,
                    version_id INTEGER NOT NULL,
                    property_key TEXT NOT NULL,
                    property_value TEXT NOT NULL,
                    PRIMARY KEY (id, version_id, property_key),
                    FOREIGN KEY (id, version_id) REFERENCES component(id, version_id)
                )
                """);

            // Create component-element junction table
            handle.execute("""
                CREATE TABLE IF NOT EXISTS component_element (
                    component_id TEXT NOT NULL,
                    component_version INTEGER NOT NULL,
                    element_id TEXT NOT NULL,
                    element_version INTEGER NOT NULL,
                    element_type TEXT NOT NULL,
                    PRIMARY KEY (component_id, component_version, element_id, element_version),
                    FOREIGN KEY (component_id, component_version) REFERENCES component(id, version_id)
                )
                """);

            // Create indices for better query performance
            handle.execute("CREATE INDEX IF NOT EXISTS idx_node_expired ON node(id, expired)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_edge_expired ON edge(id, expired)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_component_expired ON component(id, expired)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_edge_source ON edge(source_id, source_version_id)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_edge_target ON edge(target_id, target_version_id)");
        }));
    }

    @Override
    public Session create() {

        return new SqliteSession(jdbi);
    }
}
