/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TinkerpopSessionFactory.
 */
@DisplayName("TinkerpopSessionFactory Unit Tests")
final class TinkerpopSessionFactoryTest {

    private TinkerpopSessionFactory sessionFactory;

    @BeforeEach
    void before() {
        sessionFactory = new TinkerpopSessionFactory();
    }

    @Test
    @DisplayName("create returns new TinkerpopSession with TinkerGraph")
    void testCreateReturnsNewSession() {

        final var session = sessionFactory.create();

        assertNotNull(session);
        assertInstanceOf(TinkerpopSession.class, session);
    }

    @Test
    @DisplayName("create returns different sessions on multiple calls")
    void testCreateReturnsDifferentSessions() {

        final var session1 = sessionFactory.create();
        final var session2 = sessionFactory.create();

        assertNotSame(session1, session2);
    }

    @Test
    @DisplayName("sessions support commit and rollback operations")
    void testSessionsupportTransactionOperations() {

        final var session = sessionFactory.create();

        // These operations should not throw
        assertDoesNotThrow(session::commit);
        assertDoesNotThrow(session::rollback);
        assertDoesNotThrow(session::close);
    }
}
