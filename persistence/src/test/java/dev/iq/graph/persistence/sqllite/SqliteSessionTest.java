/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SqliteSession transaction management.
 */
@DisplayName("SqliteSession Unit Tests")
final class SqliteSessionTest {

    private Jdbi mockJdbi;
    private Handle mockHandle;
    private SqliteSession session;

    @BeforeEach
    void setUp() {
        mockJdbi = mock(Jdbi.class);
        mockHandle = mock(Handle.class);
        when(mockJdbi.open()).thenReturn(mockHandle);

        session = new SqliteSession(mockJdbi);
    }

    @Test
    @DisplayName("constructor opens handle and begins transaction")
    void testConstructorOpensHandleAndBeginsTransaction() {

        verify(mockJdbi).open();
        verify(mockHandle).begin();
    }

    @Test
    @DisplayName("commit commits transaction")
    void testCommit() {

        session.commit();

        verify(mockHandle).commit();
    }

    @Test
    @DisplayName("rollback rolls back transaction")
    void testRollback() {

        session.rollback();

        verify(mockHandle).rollback();
    }

    @Test
    @DisplayName("close rolls back open transaction and closes handle")
    void testCloseRollsBackOpenTransaction() {

        when(mockHandle.isInTransaction()).thenReturn(true);

        session.close();

        verify(mockHandle).isInTransaction();
        verify(mockHandle).rollback();
        verify(mockHandle).close();
    }

    @Test
    @DisplayName("close after commit only closes handle")
    void testCloseAfterCommit() {

        when(mockHandle.isInTransaction()).thenReturn(false);

        session.commit();
        session.close();

        verify(mockHandle).commit();
        verify(mockHandle).isInTransaction();
        verify(mockHandle, never()).rollback();
        verify(mockHandle).close();
    }

    @Test
    @DisplayName("handle returns the underlying handle")
    void testHandleReturnsHandle() {

        assertSame(mockHandle, session.handle());
    }

    @Test
    @DisplayName("multiple commits are allowed")
    void testMultipleCommits() {

        session.commit();
        session.commit();

        verify(mockHandle, times(2)).commit();
    }

    @Test
    @DisplayName("multiple rollbacks are allowed")
    void testMultipleRollbacks() {

        session.rollback();
        session.rollback();

        verify(mockHandle, times(2)).rollback();
    }

    @Test
    @DisplayName("operations after close should not throw")
    void testOperationsAfterClose() {

        session.close();

        // These should not throw even if connection is closed
        assertDoesNotThrow(() -> session.commit());
        assertDoesNotThrow(() -> session.rollback());
        assertDoesNotThrow(() -> session.close());
    }
}
