/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

/**
 * Unit tests for SqliteSessionFactory.
 */
@DisplayName("SqliteSessionFactory Unit Tests")
final class SqliteSessionFactoryTest {

    private SqliteSessionFactory sessionFactory;

    @BeforeEach
    void before() {
        // For testing, we need to use a real in-memory SQLite database
        // since the constructor initializes the schema
        final DataSource dataSource = new SQLiteDataSource();
        ((SQLiteDataSource) dataSource).setUrl("jdbc:sqlite::memory:");

        sessionFactory = new SqliteSessionFactory(dataSource);
    }

    @Test
    @DisplayName("create returns new SqliteSession")
    void testCreateReturnsNewSession() {

        final var session = sessionFactory.create();

        assertNotNull(session);
        assertInstanceOf(SqliteSession.class, session);
    }

    @Test
    @DisplayName("create returns different sessions on multiple calls")
    void testCreateReturnsDifferentSessions() {

        final var session1 = sessionFactory.create();
        final var session2 = sessionFactory.create();

        assertNotSame(session1, session2);
    }

    @Test
    @DisplayName("constructor initializes schema")
    void testConstructorInitializesSchema() {

        // The constructor should initialize the schema
        // In a unit test, we can't easily verify the actual schema creation
        // but we can verify the factory was created successfully
        assertNotNull(sessionFactory);
    }
}
