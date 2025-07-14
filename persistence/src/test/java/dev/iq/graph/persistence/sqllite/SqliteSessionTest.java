package dev.iq.graph.persistence.sqllite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqliteSession transaction management.
 */
@DisplayName("SqliteSession Unit Tests")
final class SqliteSessionTest {

    private Connection mockConnection;
    private SqliteSession session;

    @BeforeEach
    void setUp() throws SQLException {
        mockConnection = mock(Connection.class);
        when(mockConnection.getAutoCommit()).thenReturn(true);
        
        session = new SqliteSession(mockConnection);
    }

    @Test
    @DisplayName("constructor disables auto-commit")
    void testConstructorDisablesAutoCommit() throws SQLException {
        
        verify(mockConnection).setAutoCommit(false);
    }

    @Test
    @DisplayName("commit commits transaction")
    void testCommit() throws SQLException {
        
        session.commit();
        
        verify(mockConnection).commit();
    }

    @Test
    @DisplayName("rollback rolls back transaction")
    void testRollback() throws SQLException {
        
        session.rollback();
        
        verify(mockConnection).rollback();
    }

    @Test
    @DisplayName("close closes connection without automatic rollback")
    void testCloseClosesConnection() throws SQLException {
        
        when(mockConnection.isClosed()).thenReturn(false);
        
        session.close();
        
        verify(mockConnection, never()).rollback();
        verify(mockConnection).close();
    }

    @Test
    @DisplayName("close does not rollback if connection is already closed")
    void testCloseSkipsRollbackIfClosed() throws SQLException {
        
        when(mockConnection.isClosed()).thenReturn(true);
        
        session.close();
        
        verify(mockConnection, never()).rollback();
        verify(mockConnection).close();
    }

    @Test
    @DisplayName("close after commit only closes connection")
    void testCloseAfterCommit() throws SQLException {
        
        when(mockConnection.isClosed()).thenReturn(false);
        
        session.commit();
        session.close();
        
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
        verify(mockConnection).close();
    }

    @Test
    @DisplayName("connection returns the underlying connection")
    void testConnectionReturnsConnection() {
        
        assertSame(mockConnection, session.connection());
    }

    @Test
    @DisplayName("multiple commits are allowed")
    void testMultipleCommits() throws SQLException {
        
        session.commit();
        session.commit();
        
        verify(mockConnection, times(2)).commit();
    }

    @Test
    @DisplayName("multiple rollbacks are allowed")
    void testMultipleRollbacks() throws SQLException {
        
        session.rollback();
        session.rollback();
        
        verify(mockConnection, times(2)).rollback();
    }

    @Test
    @DisplayName("operations after close should not throw")
    void testOperationsAfterClose() throws SQLException {
        
        when(mockConnection.isClosed()).thenReturn(false, true);
        
        session.close();
        
        // These should not throw even if connection is closed
        assertDoesNotThrow(() -> session.commit());
        assertDoesNotThrow(() -> session.rollback());
        assertDoesNotThrow(() -> session.close());
    }
}