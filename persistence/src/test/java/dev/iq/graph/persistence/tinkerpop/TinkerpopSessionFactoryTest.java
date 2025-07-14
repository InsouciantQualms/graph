package dev.iq.graph.persistence.tinkerpop;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TinkerpopSessionFactory.
 */
@DisplayName("TinkerpopSessionFactory Unit Tests")
final class TinkerpopSessionFactoryTest {

    private TinkerpopSessionFactory sessionFactory;

    @BeforeEach
    void setUp() {
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
        assertDoesNotThrow(() -> session.commit());
        assertDoesNotThrow(() -> session.rollback());
        assertDoesNotThrow(() -> session.close());
    }
}