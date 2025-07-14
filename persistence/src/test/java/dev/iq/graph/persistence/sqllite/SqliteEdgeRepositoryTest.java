package dev.iq.graph.persistence.sqllite;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleEdge;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqliteEdgeRepository.
 */
@DisplayName("SqliteEdgeRepository Unit Tests")
final class SqliteEdgeRepositoryTest {

    private SqliteSession mockSession;
    private SqliteNodeRepository mockNodeRepository;
    private Connection mockConnection;
    private PreparedStatement mockStatement;
    private PreparedStatement mockPropertiesStatement;
    private ResultSet mockResultSet;
    private ResultSet mockPropertiesResultSet;
    private SqliteEdgeRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        mockSession = mock(SqliteSession.class);
        mockNodeRepository = mock(SqliteNodeRepository.class);
        mockConnection = mock(Connection.class);
        mockStatement = mock(PreparedStatement.class);
        mockPropertiesStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        mockPropertiesResultSet = mock(ResultSet.class);

        when(mockSession.connection()).thenReturn(mockConnection);
        
        // Return different statements for different queries
        when(mockConnection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("edge_properties")) {
                return mockPropertiesStatement;
            }
            return mockStatement;
        });
        
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockPropertiesStatement.executeQuery()).thenReturn(mockPropertiesResultSet);

        repository = new SqliteEdgeRepository(mockSession, mockNodeRepository);
    }

    @Test
    @DisplayName("save inserts edge into database")
    void testSaveInsertsEdge() throws SQLException {

        final var edge = createTestEdge();
        
        // Mock properties result for save operation
        when(mockPropertiesResultSet.next()).thenReturn(false);

        final var result = repository.save(edge);

        // Verify edge data was set in prepared statement
        verify(mockStatement, atLeastOnce()).setString(1, edge.locator().id().id());
        verify(mockStatement, atLeastOnce()).setInt(2, edge.locator().version());
        verify(mockStatement, atLeastOnce()).setString(3, edge.source().locator().id().id());
        verify(mockStatement, atLeastOnce()).setInt(4, edge.source().locator().version());
        verify(mockStatement, atLeastOnce()).setString(5, edge.target().locator().id().id());
        verify(mockStatement, atLeastOnce()).setInt(6, edge.target().locator().version());
        verify(mockStatement, atLeastOnce()).setString(7, edge.created().toString());
        verify(mockStatement, atLeastOnce()).setString(8, null); // expired
        verify(mockStatement, atLeastOnce()).executeUpdate();

        // Verify result is the same edge
        assertEquals(edge, result);
    }

    @Test
    @DisplayName("findActive returns active edge")
    void testFindActiveReturnsEdge() throws SQLException {

        final var edgeId = new NanoId("test-edge");
        final var created = Instant.now();
        final var source = createTestNode();
        final var target = createTestNode();

        // Mock result set to return an edge (only once)
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("id")).thenReturn(edgeId.id());
        when(mockResultSet.getInt("version_id")).thenReturn(1);
        when(mockResultSet.getString("source_id")).thenReturn(source.locator().id().id());
        when(mockResultSet.getInt("source_version_id")).thenReturn(source.locator().version());
        when(mockResultSet.getString("target_id")).thenReturn(target.locator().id().id());
        when(mockResultSet.getInt("target_version_id")).thenReturn(target.locator().version());
        when(mockResultSet.getString("created")).thenReturn(created.toString());
        when(mockResultSet.getString("expired")).thenReturn(null);

        // Mock node repository to return source and target
        when(mockNodeRepository.find(eq(source.locator()))).thenReturn(Optional.of(source));
        when(mockNodeRepository.find(eq(target.locator()))).thenReturn(Optional.of(target));
        
        // Mock properties for the edge
        when(mockPropertiesResultSet.next())
            .thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockPropertiesResultSet.getString("property_key"))
            .thenReturn("data._type").thenReturn("data.root");
        when(mockPropertiesResultSet.getString("property_value"))
            .thenReturn("java.lang.String").thenReturn("test-edge");

        final var result = repository.findActive(edgeId);

        assertTrue(result.isPresent());
        assertEquals(edgeId, result.get().locator().id());
        assertEquals(1, result.get().locator().version());
        assertFalse(result.get().expired().isPresent());
    }

    @Test
    @DisplayName("findActive returns empty when no active edge exists")
    void testFindActiveReturnsEmpty() throws SQLException {

        when(mockResultSet.next()).thenReturn(false);

        final var result = repository.findActive(NanoId.generate());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findAll returns all versions of an edge")
    void testFindAllReturnsAllVersions() throws SQLException {

        final var edgeId = new NanoId("test-edge");
        final var created = Instant.now();
        final var source = createTestNode();
        final var target = createTestNode();

        // Mock result set to return two versions
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getString("id")).thenReturn(edgeId.id());
        when(mockResultSet.getInt("version_id")).thenReturn(1, 2);
        when(mockResultSet.getString("source_id")).thenReturn(source.locator().id().id());
        when(mockResultSet.getInt("source_version_id")).thenReturn(source.locator().version());
        when(mockResultSet.getString("target_id")).thenReturn(target.locator().id().id());
        when(mockResultSet.getInt("target_version_id")).thenReturn(target.locator().version());
        when(mockResultSet.getString("created")).thenReturn(created.toString());
        when(mockResultSet.getString("expired")).thenReturn(created.plusSeconds(10).toString(), (String) null);

        // Mock node repository
        when(mockNodeRepository.find(any(Locator.class)))
            .thenReturn(Optional.of(source))
            .thenReturn(Optional.of(target))
            .thenReturn(Optional.of(source))
            .thenReturn(Optional.of(target));
        
        // Mock properties result set to return type and value information for both edges
        when(mockPropertiesResultSet.next())
            .thenReturn(true).thenReturn(true).thenReturn(false)  // First edge: type, value, done
            .thenReturn(true).thenReturn(true).thenReturn(false); // Second edge: type, value, done
        when(mockPropertiesResultSet.getString("property_key"))
            .thenReturn("data._type").thenReturn("data.root")
            .thenReturn("data._type").thenReturn("data.root");
        when(mockPropertiesResultSet.getString("property_value"))
            .thenReturn("java.lang.String").thenReturn("test-edge")
            .thenReturn("java.lang.String").thenReturn("test-edge");

        final var results = repository.findAll(edgeId);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).locator().version());
        assertEquals(2, results.get(1).locator().version());
        assertTrue(results.get(0).expired().isPresent());
        assertFalse(results.get(1).expired().isPresent());
    }

    @Test
    @DisplayName("createEdgeFromResultSet throws when source node not found")
    void testCreateEdgeThrowsWhenSourceNotFound() throws SQLException {

        final var edgeId = new NanoId("test-edge");
        final var source = createTestNode();
        final var target = createTestNode();

        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("id")).thenReturn(edgeId.id());
        when(mockResultSet.getInt("version_id")).thenReturn(1);
        when(mockResultSet.getString("source_id")).thenReturn(source.locator().id().id());
        when(mockResultSet.getInt("source_version_id")).thenReturn(source.locator().version());
        when(mockResultSet.getString("target_id")).thenReturn(target.locator().id().id());
        when(mockResultSet.getInt("target_version_id")).thenReturn(target.locator().version());
        when(mockResultSet.getString("created")).thenReturn(Instant.now().toString());

        // Mock node repository to return empty for source
        when(mockNodeRepository.find(eq(source.locator()))).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> repository.findActive(edgeId));
    }

    @Test
    @DisplayName("delete removes all versions of edge")
    void testDeleteRemovesEdge() throws SQLException {

        when(mockStatement.executeUpdate()).thenReturn(2);

        final var edgeId = NanoId.generate();
        final var result = repository.delete(edgeId);

        assertTrue(result);
        verify(mockStatement).setString(1, edgeId.id());
        verify(mockStatement).executeUpdate();
    }

    @Test
    @DisplayName("expire updates active edge with expiration timestamp")
    void testExpireUpdatesEdge() throws SQLException {

        when(mockStatement.executeUpdate()).thenReturn(1);

        final var edgeId = NanoId.generate();
        final var expiredAt = Instant.now();

        final var result = repository.expire(edgeId, expiredAt);

        assertTrue(result);
        verify(mockStatement).setString(1, expiredAt.toString());
        verify(mockStatement).setString(2, edgeId.id());
    }

    private SimpleEdge createTestEdge() {

        final var source = createTestNode();
        final var target = createTestNode();
        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-edge");
        return new SimpleEdge(locator, source, target, data, Instant.now(), Optional.empty());
    }

    private Node createTestNode() {

        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-node");
        return new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());
    }
}