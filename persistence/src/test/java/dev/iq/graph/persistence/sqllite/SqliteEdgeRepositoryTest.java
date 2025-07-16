/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


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

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.ResultIterable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqliteEdgeRepository.
 */
@DisplayName("SqliteEdgeRepository Unit Tests")
final class SqliteEdgeRepositoryTest {

    private SqliteSession mockSession;
    private SqliteNodeRepository mockNodeRepository;
    private Handle mockHandle;
    private Update mockUpdate;
    private Query mockQuery;
    private PreparedBatch mockBatch;
    private ResultIterable mockResultIterable;
    private SqliteEdgeRepository repository;

    @BeforeEach
    void setUp() {
        mockSession = mock(SqliteSession.class);
        mockNodeRepository = mock(SqliteNodeRepository.class);
        mockHandle = mock(Handle.class);
        mockUpdate = mock(Update.class);
        mockQuery = mock(Query.class);
        mockBatch = mock(PreparedBatch.class);
        mockResultIterable = mock(ResultIterable.class);

        when(mockSession.handle()).thenReturn(mockHandle);
        when(mockHandle.createUpdate(anyString())).thenReturn(mockUpdate);
        when(mockHandle.createQuery(anyString())).thenReturn(mockQuery);
        when(mockHandle.prepareBatch(anyString())).thenReturn(mockBatch);
        
        // Setup fluent API for Update
        when(mockUpdate.bind(anyString(), anyString())).thenReturn(mockUpdate);
        when(mockUpdate.bind(anyString(), anyInt())).thenReturn(mockUpdate);
        when(mockUpdate.bind(anyString(), (Object) any())).thenReturn(mockUpdate);
        when(mockUpdate.bind(anyString(), (String) eq(null))).thenReturn(mockUpdate);
        
        // Setup fluent API for PreparedBatch
        when(mockBatch.bind(anyString(), anyString())).thenReturn(mockBatch);
        when(mockBatch.bind(anyString(), anyInt())).thenReturn(mockBatch);
        when(mockBatch.bind(anyString(), (Object) any())).thenReturn(mockBatch);
        when(mockBatch.add()).thenReturn(mockBatch);
        
        // Setup fluent API for Query  
        when(mockQuery.bind(anyString(), anyString())).thenReturn(mockQuery);
        when(mockQuery.bind(anyString(), anyInt())).thenReturn(mockQuery);
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(any(RowMapper.class))).thenReturn(mockResultIterable);

        repository = new SqliteEdgeRepository(mockSession, mockNodeRepository);
    }

    @Test
    @DisplayName("save inserts edge into database")
    void testSaveInsertsEdge() {

        final var edge = createTestEdge();
        
        when(mockUpdate.execute()).thenReturn(1);
        when(mockBatch.execute()).thenReturn(new int[]{1, 1}); // For properties

        final var result = repository.save(edge);

        // Verify edge data was bound to update
        verify(mockUpdate).bind("id", edge.locator().id().id());
        verify(mockUpdate).bind("version_id", edge.locator().version());
        verify(mockUpdate).bind("source_id", edge.source().locator().id().id());
        verify(mockUpdate).bind("source_version_id", edge.source().locator().version());
        verify(mockUpdate).bind("target_id", edge.target().locator().id().id());
        verify(mockUpdate).bind("target_version_id", edge.target().locator().version());
        verify(mockUpdate).bind("created", edge.created().toString());
        verify(mockUpdate).bind("expired", (String) null);
        verify(mockUpdate).execute();

        // Verify properties were saved
        verify(mockBatch, atLeastOnce()).execute();

        // Verify result is the same edge
        assertEquals(edge, result);
    }

    @Test
    @DisplayName("findActive returns active edge")
    void testFindActiveReturnsEdge() {

        final var edgeId = new NanoId("test-edge");
        final var created = Instant.now();
        final var source = createTestNode();
        final var target = createTestNode();

        // Create expected edge
        final var locator = new Locator(edgeId, 1);
        final var data = new SimpleData(String.class, "test-edge");
        final var expectedEdge = new SimpleEdge(locator, source, target, data, created, Optional.empty());

        // Mock query to return bound query
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(any(RowMapper.class))).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenReturn(Optional.of(expectedEdge));

        // Mock node repository to return source and target
        when(mockNodeRepository.find(eq(source.locator()))).thenReturn(Optional.of(source));
        when(mockNodeRepository.find(eq(target.locator()))).thenReturn(Optional.of(target));
        
        // Mock properties query for loading properties
        Query mockPropertiesQuery = mock(Query.class);
        ResultIterable mockPropertiesResultIterable = mock(ResultIterable.class);
        when(mockHandle.createQuery(contains("edge_properties"))).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), (Object) any())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), anyInt())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.map(any(RowMapper.class))).thenReturn(mockPropertiesResultIterable);
        when(mockPropertiesResultIterable.list()).thenReturn(List.of());

        final var result = repository.findActive(edgeId);

        assertTrue(result.isPresent());
        assertEquals(edgeId, result.get().locator().id());
        assertEquals(1, result.get().locator().version());
        assertFalse(result.get().expired().isPresent());
        
        verify(mockQuery).bind("id", edgeId.id());
    }

    @Test
    @DisplayName("findActive returns empty when no active edge exists")
    void testFindActiveReturnsEmpty() {

        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(any(RowMapper.class))).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenReturn(Optional.empty());

        final var result = repository.findActive(NanoId.generate());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findAll returns all versions of an edge")
    void testFindAllReturnsAllVersions() {

        final var edgeId = new NanoId("test-edge");
        final var created = Instant.now();
        final var source = createTestNode();
        final var target = createTestNode();

        // Create expected edges
        final var locator1 = new Locator(edgeId, 1);
        final var locator2 = new Locator(edgeId, 2);
        final var data = new SimpleData(String.class, "test-edge");
        final var edge1 = new SimpleEdge(locator1, source, target, data, created, Optional.of(created.plusSeconds(10)));
        final var edge2 = new SimpleEdge(locator2, source, target, data, created, Optional.empty());

        // Mock query to return edges
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(any(RowMapper.class))).thenReturn(mockResultIterable);
        when(mockResultIterable.list()).thenReturn(List.of(edge1, edge2));

        // Mock node repository
        when(mockNodeRepository.find(any(Locator.class))).thenReturn(Optional.of(source), Optional.of(target));
        
        // Mock properties query for loading properties
        Query mockPropertiesQuery = mock(Query.class);
        ResultIterable mockPropertiesResultIterable = mock(ResultIterable.class);
        when(mockHandle.createQuery(contains("edge_properties"))).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), (Object) any())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), anyInt())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.map(any(RowMapper.class))).thenReturn(mockPropertiesResultIterable);
        when(mockPropertiesResultIterable.list()).thenReturn(List.of());

        final var results = repository.findAll(edgeId);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).locator().version());
        assertEquals(2, results.get(1).locator().version());
        assertTrue(results.get(0).expired().isPresent());
        assertFalse(results.get(1).expired().isPresent());
    }

    @Test
    @DisplayName("createEdgeFromResultSet throws when source node not found")
    void testCreateEdgeThrowsWhenSourceNotFound() {

        final var edgeId = new NanoId("test-edge");
        final var source = createTestNode();
        final var target = createTestNode();

        // Mock query to throw exception when mapping
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(any(RowMapper.class))).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenThrow(new RuntimeException("missing source " + source.locator().id()));

        // Mock node repository to return empty for source
        when(mockNodeRepository.find(eq(source.locator()))).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> repository.findActive(edgeId));
    }

    @Test
    @DisplayName("delete removes all versions of edge")
    void testDeleteRemovesEdge() {

        final var edgeId = NanoId.generate();
        when(mockUpdate.execute()).thenReturn(2);

        final var result = repository.delete(edgeId);

        assertTrue(result);
        verify(mockUpdate).bind("id", edgeId.id());
        verify(mockUpdate).execute();
    }

    @Test
    @DisplayName("expire updates active edge with expiration timestamp")
    void testExpireUpdatesEdge() {

        final var edgeId = NanoId.generate();
        final var expiredAt = Instant.now();
        when(mockUpdate.execute()).thenReturn(1);

        final var result = repository.expire(edgeId, expiredAt);

        assertTrue(result);
        verify(mockUpdate).bind("expired", expiredAt.toString());
        verify(mockUpdate).bind("id", edgeId.id());
        verify(mockUpdate).execute();
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