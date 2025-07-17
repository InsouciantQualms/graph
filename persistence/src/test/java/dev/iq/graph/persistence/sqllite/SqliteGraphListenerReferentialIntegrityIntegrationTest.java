/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import dev.iq.common.persist.Session;
import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;

/**
 * Integration tests for SqliteGraphRepository to validate
 * referential integrity when using graph listeners.
 */
@DisplayName("SQLite Graph Listener Referential Integrity Integration Tests")
final class SqliteGraphListenerReferentialIntegrityIntegrationTest
        extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    private static SqliteSessionFactory sessionFactory;
    private Session currentSession;

    @BeforeAll
    static void setUpClass() {
        final DataSource dataSource = SqlliteTestConnectionHelper.getSharedDataSource();
        sessionFactory = new SqliteSessionFactory(dataSource);
    }

    @AfterAll
    static void tearDownClass() {
        SqlliteTestConnectionHelper.closeSharedDataSource();
    }

    @AfterEach
    void tearDown() {
        if (currentSession != null) {
            currentSession.rollback();
            currentSession.close();
            currentSession = null;
        }
    }

    @Override
    protected GraphRepository createGraphRepository() {

        // Create a new session for this test
        currentSession = sessionFactory.create();
        return SqliteGraphRepository.create((SqliteSession) currentSession);
    }
}
