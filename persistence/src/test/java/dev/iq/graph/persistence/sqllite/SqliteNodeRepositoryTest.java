package dev.iq.graph.persistence.sqllite;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqliteNodeRepository.
 */
@DisplayName("SqliteNodeRepository Unit Tests")
final class SqliteNodeRepositoryTest {

    private SqliteSession mockSession;
    private Connection mockConnection;
    private PreparedStatement mockStatement;
    private PreparedStatement mockPropertiesStatement;
    private ResultSet mockResultSet;
    private ResultSet mockPropertiesResultSet;
    private SqliteNodeRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        mockSession = mock(SqliteSession.class);
        mockConnection = mock(Connection.class);
        mockStatement = mock(PreparedStatement.class);
        mockPropertiesStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        mockPropertiesResultSet = mock(ResultSet.class);
        
        when(mockSession.connection()).thenReturn(mockConnection);
        
        // Return different statements for different queries
        when(mockConnection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("node_properties")) {
                return mockPropertiesStatement;
            }
            return mockStatement;
        });
        
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockPropertiesStatement.executeQuery()).thenReturn(mockPropertiesResultSet);
        
        repository = new SqliteNodeRepository(mockSession);
    }

    @Test
    @DisplayName("save inserts node into database")
    void testSaveInsertsNode() throws SQLException {
        
        final var node = createTestNode();
        
        final var result = repository.save(node);
        
        // Verify node data was set in prepared statement
        verify(mockStatement).setString(1, node.locator().id().id());
        verify(mockStatement).setInt(2, node.locator().version());
        verify(mockStatement).setString(3, node.created().toString());
        verify(mockStatement).setString(4, null); // expired
        verify(mockStatement).executeUpdate();
        
        // Verify result is the same node
        assertEquals(node, result);
    }

    @Test
    @DisplayName("save handles node with expired timestamp")
    void testSaveNodeWithExpired() throws SQLException {
        
        final var expired = Instant.now();
        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-value");
        final var node = new SimpleNode(locator, List.of(), data, Instant.now(), Optional.of(expired));
        
        repository.save(node);
        
        // Verify expired timestamp was set
        verify(mockStatement).setString(4, expired.toString());
    }

    @Test
    @DisplayName("findActive returns active node")
    void testFindActiveReturnsNode() throws SQLException {
        
        final var nodeId = new NanoId("test-node");
        final var created = Instant.now();
        
        // Mock result set to return a node (only once)
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("id")).thenReturn(nodeId.id());
        when(mockResultSet.getInt("version_id")).thenReturn(1);
        when(mockResultSet.getString("created")).thenReturn(created.toString());
        when(mockResultSet.getString("expired")).thenReturn(null);
        
        // Mock properties for the node
        when(mockPropertiesResultSet.next())
            .thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockPropertiesResultSet.getString("property_key"))
            .thenReturn("data._type").thenReturn("data.root");
        when(mockPropertiesResultSet.getString("property_value"))
            .thenReturn("java.lang.String").thenReturn("test-value");
        
        final var result = repository.findActive(nodeId);
        
        assertTrue(result.isPresent());
        assertEquals(nodeId, result.get().locator().id());
        assertEquals(1, result.get().locator().version());
        assertFalse(result.get().expired().isPresent());
    }

    @Test
    @DisplayName("findActive returns empty when no active node exists")
    void testFindActiveReturnsEmpty() throws SQLException {
        
        when(mockResultSet.next()).thenReturn(false);
        
        final var result = repository.findActive(NanoId.generate());
        
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findAll returns all versions of a node")
    void testFindAllReturnsAllVersions() throws SQLException {
        
        final var nodeId = new NanoId("test-node");
        final var created = Instant.now();
        
        // Mock result set to return two versions
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getString("id")).thenReturn(nodeId.id());
        when(mockResultSet.getInt("version_id")).thenReturn(1, 2);
        when(mockResultSet.getString("created")).thenReturn(created.toString());
        when(mockResultSet.getString("expired")).thenReturn(created.plusSeconds(10).toString(), (String) null);
        
        // Mock properties for both nodes
        when(mockPropertiesResultSet.next())
            .thenReturn(true).thenReturn(true).thenReturn(false)  // First node: type, value, done
            .thenReturn(true).thenReturn(true).thenReturn(false); // Second node: type, value, done
        when(mockPropertiesResultSet.getString("property_key"))
            .thenReturn("data._type").thenReturn("data.root")
            .thenReturn("data._type").thenReturn("data.root");
        when(mockPropertiesResultSet.getString("property_value"))
            .thenReturn("java.lang.String").thenReturn("test-value")
            .thenReturn("java.lang.String").thenReturn("test-value");
        
        final var results = repository.findAll(nodeId);
        
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).locator().version());
        assertEquals(2, results.get(1).locator().version());
        assertTrue(results.get(0).expired().isPresent());
        assertFalse(results.get(1).expired().isPresent());
    }

    @Test
    @DisplayName("find returns specific version of node")
    void testFindReturnsSpecificVersion() throws SQLException {
        
        final var locator = new Locator(NanoId.generate(), 2);
        final var created = Instant.now();
        
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("id")).thenReturn(locator.id().id());
        when(mockResultSet.getInt("version_id")).thenReturn(locator.version());
        when(mockResultSet.getString("created")).thenReturn(created.toString());
        when(mockResultSet.getString("expired")).thenReturn(null);
        
        // Mock properties
        when(mockPropertiesResultSet.next())
            .thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockPropertiesResultSet.getString("property_key"))
            .thenReturn("data._type").thenReturn("data.root");
        when(mockPropertiesResultSet.getString("property_value"))
            .thenReturn("java.lang.String").thenReturn("test-value");
        
        final var result = repository.find(locator);
        
        assertTrue(result.isPresent());
        assertEquals(locator, result.get().locator());
    }

    @Test
    @DisplayName("findAt returns node at specific timestamp")
    void testFindAtReturnsNodeAtTimestamp() throws SQLException {
        
        final var nodeId = new NanoId("test-node");
        final var timestamp = Instant.now();
        final var created = timestamp.minusSeconds(10);
        
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("id")).thenReturn(nodeId.id());
        when(mockResultSet.getInt("version_id")).thenReturn(1);
        when(mockResultSet.getString("created")).thenReturn(created.toString());
        when(mockResultSet.getString("expired")).thenReturn(null);
        
        // Mock properties
        when(mockPropertiesResultSet.next())
            .thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockPropertiesResultSet.getString("property_key"))
            .thenReturn("data._type").thenReturn("data.root");
        when(mockPropertiesResultSet.getString("property_value"))
            .thenReturn("java.lang.String").thenReturn("test-value");
        
        final var result = repository.findAt(nodeId, timestamp);
        
        assertTrue(result.isPresent());
        verify(mockStatement).setString(1, nodeId.id());
        verify(mockStatement).setString(2, timestamp.toString());
        verify(mockStatement).setString(3, timestamp.toString());
    }

    @Test
    @DisplayName("delete removes all versions of node")
    void testDeleteRemovesNode() throws SQLException {
        
        when(mockStatement.executeUpdate()).thenReturn(2);
        
        final var nodeId = NanoId.generate();
        final var result = repository.delete(nodeId);
        
        assertTrue(result);
        verify(mockStatement).setString(1, nodeId.id());
        verify(mockStatement).executeUpdate();
    }

    @Test
    @DisplayName("expire updates active node with expiration timestamp")
    void testExpireUpdatesNode() throws SQLException {
        
        when(mockStatement.executeUpdate()).thenReturn(1);
        
        final var nodeId = NanoId.generate();
        final var expiredAt = Instant.now();
        
        final var result = repository.expire(nodeId, expiredAt);
        
        assertTrue(result);
        verify(mockStatement).setString(1, expiredAt.toString());
        verify(mockStatement).setString(2, nodeId.id());
    }

    private SimpleNode createTestNode() {
        
        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-value");
        return new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());
    }
}