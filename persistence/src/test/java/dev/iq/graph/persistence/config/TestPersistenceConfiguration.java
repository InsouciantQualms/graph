/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.mongodb.MongoGraphRepository;
import dev.iq.graph.persistence.mongodb.MongoSession;
import dev.iq.graph.persistence.mongodb.MongoTransactionManager;
import dev.iq.graph.persistence.sqllite.SpringTransactionalSqliteSession;
import dev.iq.graph.persistence.sqllite.SqliteGraphRepository;
import dev.iq.graph.persistence.sqllite.SqliteTransactionManager;
import dev.iq.graph.persistence.tinkerpop.TinkerpopGraphRepository;
import dev.iq.graph.persistence.tinkerpop.TinkerpopSession;
import dev.iq.graph.persistence.tinkerpop.TinkerpopSessionFactory;
import dev.iq.graph.persistence.tinkerpop.TinkerpopTransactionManager;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.jdbi.v3.core.Jdbi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test configuration for persistence layer with Spring transactions.
 */
@Configuration
public class TestPersistenceConfiguration {

    /**
     * Creates TinkerGraph instance for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public final Graph tinkerpopGraph() {

        return TinkerGraph.open();
    }

    /**
     * Creates graph supplier for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public final Supplier<Graph> graphSupplier(final Graph graph) {

        return () -> graph;
    }

    /**
     * Creates TinkerPop transaction manager for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public final PlatformTransactionManager tinkerpopTransactionManager(final Supplier<Graph> graphSupplier) {

        return new TinkerpopTransactionManager(graphSupplier);
    }

    /**
     * Creates transaction template for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public final TransactionTemplate transactionTemplate(final PlatformTransactionManager transactionManager) {

        return new TransactionTemplate(transactionManager);
    }

    /**
     * Creates TinkerPop graph repository for testing.
     */
    @Bean("graphRepository")
    @Profile("tinkerpop")
    public final GraphRepository tinkerpopGraphRepository(final Graph graph) {

        final var sessionFactory = new TinkerpopSessionFactory();
        try (var session = (TinkerpopSession) sessionFactory.create()) {
            return TinkerpopGraphRepository.create(session);
        }
    }

    /**
     * Creates SQLite graph repository for testing.
     */
    @Bean("graphRepository")
    @Profile("sqlite")
    public static final GraphRepository sqliteGraphRepository(final Jdbi jdbi) {

        // Initialize schema
        initializeSqliteSchema(jdbi);

        // Create a Spring transaction-aware session
        final var session = new SpringTransactionalSqliteSession(jdbi);
        return SqliteGraphRepository.create(session);
    }

    private static void initializeSqliteSchema(final Jdbi jdbi) {

        jdbi.useHandle(handle -> {
            // Enable foreign keys for SQLite
            handle.execute("PRAGMA foreign_keys = ON");

            // Create node table
            handle.execute(
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

            // Create node properties table
            handle.execute(
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

            // Create edge table
            handle.execute(
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

            // Create edge properties table
            handle.execute(
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

            // Create component table
            handle.execute(
                    """
                    CREATE TABLE IF NOT EXISTS component (
                        id TEXT NOT NULL,
                        version_id INTEGER NOT NULL,
                        created TEXT NOT NULL,
                        expired TEXT,
                        PRIMARY KEY (id, version_id)
                    )
                    """);

            // Create component properties table
            handle.execute(
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

            // Create component-element junction table
            handle.execute(
                    """
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
        });
    }

    /**
     * Creates MongoDB graph repository for testing.
     */
    @Bean("graphRepository")
    @Profile("mongodb")
    public final GraphRepository mongoGraphRepository(final MongoClient mongoClient) {

        final var session = new MongoSession(mongoClient, "test");
        return MongoGraphRepository.create(session);
    }

    /**
     * Creates SQLite data source for testing.
     */
    @Bean
    @Profile("sqlite")
    public final DataSource sqliteDataSource() {

        // Create an in-memory SQLite database for tests
        final var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite::memory:");
        hikariConfig.setMaximumPoolSize(1);
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Creates JDBI instance for testing.
     */
    @Bean
    @Profile("sqlite")
    public final Jdbi jdbi(final DataSource dataSource) {

        return Jdbi.create(dataSource);
    }

    /**
     * Creates SQLite transaction manager for testing.
     */
    @Bean
    @Profile("sqlite")
    public final PlatformTransactionManager sqliteTransactionManager(final Jdbi jdbi) {

        return new SqliteTransactionManager(jdbi);
    }

    /**
     * Creates transaction template for testing.
     */
    @Bean
    @Profile("sqlite")
    public final TransactionTemplate sqliteTransactionTemplate(final PlatformTransactionManager transactionManager) {

        return new TransactionTemplate(transactionManager);
    }

    /**
     * Creates MongoDB client for testing.
     */
    @Bean
    @Profile("mongodb")
    public final MongoClient mongoClient() {

        // Use embedded MongoDB for tests
        return MongoClients.create("mongodb://localhost:27017");
    }

    /**
     * Creates MongoDB transaction manager for testing.
     */
    @Bean
    @Profile("mongodb")
    public final PlatformTransactionManager mongoTransactionManager(final MongoClient mongoClient) {

        return new MongoTransactionManager(mongoClient);
    }

    /**
     * Creates transaction template for testing.
     */
    @Bean
    @Profile("mongodb")
    public final TransactionTemplate mongoTransactionTemplate(final PlatformTransactionManager transactionManager) {

        return new TransactionTemplate(transactionManager);
    }
}
