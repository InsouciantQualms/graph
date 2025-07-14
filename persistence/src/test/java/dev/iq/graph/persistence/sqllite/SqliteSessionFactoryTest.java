package dev.iq.graph.persistence.sqllite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqliteSessionFactory.
 */
@DisplayName("SqliteSessionFactory Unit Tests")
final class SqliteSessionFactoryTest {

    private DataSource mockDataSource;
    private Connection mockConnection;
    private SqliteSessionFactory sessionFactory;

    @BeforeEach
    void setUp() throws SQLException {
        mockDataSource = mock(DataSource.class);
        mockConnection = mock(Connection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.getAutoCommit()).thenReturn(true);
        
        sessionFactory = new SqliteSessionFactory(mockDataSource);
    }

    @Test
    @DisplayName("create returns new SqliteSession")
    void testCreateReturnsNewSession() throws SQLException {
        
        final var session = sessionFactory.create();
        
        assertNotNull(session);
        assertInstanceOf(SqliteSession.class, session);
        
        // Verify connection was obtained from DataSource
        verify(mockDataSource).getConnection();
    }

    @Test
    @DisplayName("create returns different sessions on multiple calls")
    void testCreateReturnsDifferentSessions() throws SQLException {
        
        final var connection1 = mock(Connection.class);
        final var connection2 = mock(Connection.class);
        when(connection1.getAutoCommit()).thenReturn(true);
        when(connection2.getAutoCommit()).thenReturn(true);
        
        when(mockDataSource.getConnection()).thenReturn(connection1, connection2);
        
        final var session1 = sessionFactory.create();
        final var session2 = sessionFactory.create();
        
        assertNotSame(session1, session2);
        
        // Verify two connections were obtained
        verify(mockDataSource, times(2)).getConnection();
    }

    @Test
    @DisplayName("create propagates SQLException as RuntimeException")
    void testCreatePropagatesException() throws SQLException {
        
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
        
        assertThrows(RuntimeException.class, () -> sessionFactory.create());
    }
}