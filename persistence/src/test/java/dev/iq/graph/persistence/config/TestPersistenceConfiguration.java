/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.mongodb.MongoGraphRepository;
import dev.iq.graph.persistence.mongodb.MongoSession;
import dev.iq.graph.persistence.mongodb.MongoTransactionManager;
import dev.iq.graph.persistence.sqllite.SqliteGraphRepository;
import dev.iq.graph.persistence.sqllite.SqliteSession;
import dev.iq.graph.persistence.sqllite.SqliteSessionFactory;
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
    public Graph tinkerpopGraph() {

        return TinkerGraph.open();
    }

    /**
     * Creates graph supplier for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public Supplier<Graph> graphSupplier(final Graph graph) {

        return () -> graph;
    }

    /**
     * Creates TinkerPop transaction manager for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public PlatformTransactionManager tinkerpopTransactionManager(final Supplier<Graph> graphSupplier) {

        return new TinkerpopTransactionManager(graphSupplier);
    }

    /**
     * Creates transaction template for testing.
     */
    @Bean
    @Profile("tinkerpop")
    public TransactionTemplate transactionTemplate(final PlatformTransactionManager transactionManager) {

        return new TransactionTemplate(transactionManager);
    }

    /**
     * Creates TinkerPop graph repository for testing.
     */
    @Bean("graphRepository")
    @Profile("tinkerpop")
    public GraphRepository tinkerpopGraphRepository(final Graph graph) {

        final var sessionFactory = new TinkerpopSessionFactory();
        try (var session = (TinkerpopSession) sessionFactory.create()) {
            return (TinkerpopGraphRepository) TinkerpopGraphRepository.create(session);
        }
    }

    /**
     * Creates SQLite graph repository for testing.
     */
    @Bean("graphRepository")
    @Profile("sqlite")
    public GraphRepository sqliteGraphRepository(final DataSource dataSource) {

        // Initialize schema
        final var sessionFactory = new SqliteSessionFactory(dataSource);
        try (var session = sessionFactory.create()) {
            return (SqliteGraphRepository) SqliteGraphRepository.create((SqliteSession) session);
        }
    }

    /**
     * Creates MongoDB graph repository for testing.
     */
    @Bean("graphRepository")
    @Profile("mongodb")
    public GraphRepository mongoGraphRepository(final MongoClient mongoClient) {

        final var session = new MongoSession(mongoClient, "test");
        return (MongoGraphRepository) MongoGraphRepository.create(session);
    }

    /**
     * Creates SQLite data source for testing.
     */
    @Bean
    @Profile("sqlite")
    public DataSource sqliteDataSource() {

        // Create an in-memory SQLite database for tests
        final var hikariConfig = new com.zaxxer.hikari.HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite::memory:");
        hikariConfig.setMaximumPoolSize(1);
        return new com.zaxxer.hikari.HikariDataSource(hikariConfig);
    }

    /**
     * Creates JDBI instance for testing.
     */
    @Bean
    @Profile("sqlite")
    public Jdbi jdbi(final DataSource dataSource) {

        return Jdbi.create(dataSource);
    }

    /**
     * Creates SQLite transaction manager for testing.
     */
    @Bean
    @Profile("sqlite")
    public PlatformTransactionManager sqliteTransactionManager(final Jdbi jdbi) {

        return new SqliteTransactionManager(jdbi);
    }

    /**
     * Creates transaction template for testing.
     */
    @Bean
    @Profile("sqlite")
    public TransactionTemplate sqliteTransactionTemplate(final PlatformTransactionManager transactionManager) {

        return new TransactionTemplate(transactionManager);
    }

    /**
     * Creates MongoDB client for testing.
     */
    @Bean
    @Profile("mongodb")
    public MongoClient mongoClient() {

        // Use embedded MongoDB for tests
        return MongoClients.create("mongodb://localhost:27017");
    }

    /**
     * Creates MongoDB transaction manager for testing.
     */
    @Bean
    @Profile("mongodb")
    public PlatformTransactionManager mongoTransactionManager(final MongoClient mongoClient) {

        return new MongoTransactionManager(mongoClient);
    }

    /**
     * Creates transaction template for testing.
     */
    @Bean
    @Profile("mongodb")
    public TransactionTemplate mongoTransactionTemplate(final PlatformTransactionManager transactionManager) {

        return new TransactionTemplate(transactionManager);
    }
}
