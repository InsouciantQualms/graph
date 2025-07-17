/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import static dev.iq.graph.persistence.sqllite.MockHelper.mockResultIterable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.ResultIterable;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;

/**
 * Unit tests for SqliteNodeRepository.
 */
@DisplayName("SqliteNodeRepository Unit Tests")
final class SqliteNodeRepositoryTest {

    private Handle mockHandle;
    private Query mockQuery;
    private Update mockUpdate;
    private PreparedBatch mockBatch;
    private ResultIterable<SimpleNode> mockResultIterable;
    private SqliteNodeRepository repository;

    @BeforeEach
    void setUp() {
        final SqliteSession mockSession = mock(SqliteSession.class);
        mockHandle = mock(Handle.class);
        mockQuery = mock(Query.class);
        mockUpdate = mock(Update.class);
        mockBatch = mock(PreparedBatch.class);
        mockResultIterable = mockResultIterable();

        when(mockSession.handle()).thenReturn(mockHandle);

        // Set up default behavior for query and update
        when(mockHandle.createQuery(anyString())).thenReturn(mockQuery);
        when(mockHandle.createUpdate(anyString())).thenReturn(mockUpdate);
        when(mockHandle.prepareBatch(anyString())).thenReturn(mockBatch);

        // Set up chaining for query
        when(mockQuery.bind(anyString(), anyString())).thenReturn(mockQuery);
        when(mockQuery.bind(anyString(), anyInt())).thenReturn(mockQuery);
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(ArgumentMatchers.<RowMapper<SimpleNode>>any())).thenReturn(mockResultIterable);

        // Set up chaining for update
        when(mockUpdate.bind(anyString(), anyString())).thenReturn(mockUpdate);
        when(mockUpdate.bind(anyString(), anyInt())).thenReturn(mockUpdate);
        when(mockUpdate.bind(anyString(), (Object) any())).thenReturn(mockUpdate);
        when(mockUpdate.bind(anyString(), (String) eq(null))).thenReturn(mockUpdate);

        // Set up chaining for batch
        when(mockBatch.bind(anyString(), anyString())).thenReturn(mockBatch);
        when(mockBatch.bind(anyString(), anyInt())).thenReturn(mockBatch);
        when(mockBatch.bind(anyString(), (Object) any())).thenReturn(mockBatch);
        when(mockBatch.add()).thenReturn(mockBatch);

        repository = new SqliteNodeRepository(mockSession);
    }

    @Test
    @DisplayName("save inserts node into database")
    void testSaveInsertsNode() {

        final var node = createTestNode();

        when(mockUpdate.execute()).thenReturn(1);
        when(mockBatch.execute()).thenReturn(new int[] {1, 1}); // For properties

        final var result = repository.save(node);

        // Verify node data was bound to update
        verify(mockUpdate).bind("id", node.locator().id().id());
        verify(mockUpdate).bind("version_id", node.locator().version());
        verify(mockUpdate).bind("created", node.created().toString());
        verify(mockUpdate).bind("expired", (String) null);
        verify(mockUpdate).execute();

        // Verify properties were saved
        verify(mockBatch, atLeastOnce()).execute();

        // Verify result is the same node
        assertEquals(node, result);
    }

    @Test
    @DisplayName("save handles node with expired timestamp")
    void testSaveNodeWithExpired() {

        final var expired = Instant.now();
        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-value");
        final var node = new SimpleNode(locator, List.of(), data, Instant.now(), Optional.of(expired));

        when(mockUpdate.execute()).thenReturn(1);
        when(mockBatch.execute()).thenReturn(new int[] {1, 1});

        repository.save(node);

        // Verify expired timestamp was bound
        verify(mockUpdate).bind("expired", expired.toString());
    }

    @Test
    @DisplayName("findActive returns active node")
    void testFindActiveReturnsNode() {

        final var nodeId = new NanoId("test-node");
        final var created = Instant.now();

        // Create expected node
        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-value");
        final var expectedNode = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        // Mock query to return node
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(ArgumentMatchers.<RowMapper<SimpleNode>>any())).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenReturn(Optional.of(expectedNode));

        // Mock properties query for loading properties
        final var mockPropertiesQuery = mock(Query.class);
        final ResultIterable<Object> mockPropertiesResultIterable = mockResultIterable();
        when(mockHandle.createQuery(contains("node_properties"))).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), (Object) any())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), anyInt())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.map(ArgumentMatchers.<RowMapper<Object>>any()))
                .thenReturn(mockPropertiesResultIterable);
        when(mockPropertiesResultIterable.list()).thenReturn(List.of());

        final var result = repository.findActive(nodeId);

        assertTrue(result.isPresent());
        assertEquals(nodeId, result.get().locator().id());
        assertEquals(1, result.get().locator().version());
        assertFalse(result.get().expired().isPresent());

        verify(mockQuery).bind("id", nodeId.id());
    }

    @Test
    @DisplayName("findActive returns empty when no active node exists")
    void testFindActiveReturnsEmpty() {

        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(ArgumentMatchers.<RowMapper<SimpleNode>>any())).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenReturn(Optional.empty());

        final var result = repository.findActive(NanoId.generate());

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findAll returns all versions of a node")
    void testFindAllReturnsAllVersions() {

        final var nodeId = new NanoId("test-node");
        final var created = Instant.now();

        // Create expected nodes
        final var locator1 = new Locator(nodeId, 1);
        final var locator2 = new Locator(nodeId, 2);
        final var data = new SimpleData(String.class, "test-value");
        final var node1 = new SimpleNode(locator1, List.of(), data, created, Optional.of(created.plusSeconds(10)));
        final var node2 = new SimpleNode(locator2, List.of(), data, created, Optional.empty());

        // Mock query to return nodes
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(ArgumentMatchers.<RowMapper<SimpleNode>>any())).thenReturn(mockResultIterable);
        when(mockResultIterable.list()).thenReturn(List.of(node1, node2));

        // Mock properties query
        final var mockPropertiesQuery = mock(Query.class);
        final ResultIterable<Object> mockPropertiesResultIterable = mockResultIterable();
        when(mockHandle.createQuery(contains("node_properties"))).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), (Object) any())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), anyInt())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.map(ArgumentMatchers.<RowMapper<Object>>any()))
                .thenReturn(mockPropertiesResultIterable);
        when(mockPropertiesResultIterable.list()).thenReturn(List.of());

        final var results = repository.findAll(nodeId);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).locator().version());
        assertEquals(2, results.get(1).locator().version());
        assertTrue(results.get(0).expired().isPresent());
        assertFalse(results.get(1).expired().isPresent());
    }

    @Test
    @DisplayName("find returns specific version of node")
    void testFindReturnsSpecificVersion() {

        final var locator = new Locator(NanoId.generate(), 2);
        final var created = Instant.now();
        final var data = new SimpleData(String.class, "test-value");
        final var expectedNode = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        // Mock query to return node
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.bind(anyString(), anyInt())).thenReturn(mockQuery);
        when(mockQuery.map(ArgumentMatchers.<RowMapper<SimpleNode>>any())).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenReturn(Optional.of(expectedNode));

        // Mock properties query
        final var mockPropertiesQuery = mock(Query.class);
        final ResultIterable<Object> mockPropertiesResultIterable = mockResultIterable();
        when(mockHandle.createQuery(contains("node_properties"))).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), (Object) any())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), anyInt())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.map(ArgumentMatchers.<RowMapper<Object>>any()))
                .thenReturn(mockPropertiesResultIterable);
        when(mockPropertiesResultIterable.list()).thenReturn(List.of());

        final var result = repository.find(locator);

        assertTrue(result.isPresent());
        assertEquals(locator, result.get().locator());
    }

    @Test
    @DisplayName("findAt returns node at specific timestamp")
    void testFindAtReturnsNodeAtTimestamp() {

        final var nodeId = new NanoId("test-node");
        final var timestamp = Instant.now();
        final var created = timestamp.minusSeconds(10);

        final var locator = new Locator(nodeId, 1);
        final var data = new SimpleData(String.class, "test-value");
        final var expectedNode = new SimpleNode(locator, List.of(), data, created, Optional.empty());

        // Mock query to return node
        when(mockQuery.bind(anyString(), (Object) any())).thenReturn(mockQuery);
        when(mockQuery.map(ArgumentMatchers.<RowMapper<SimpleNode>>any())).thenReturn(mockResultIterable);
        when(mockResultIterable.findOne()).thenReturn(Optional.of(expectedNode));

        // Mock properties query
        final var mockPropertiesQuery = mock(Query.class);
        final ResultIterable<Object> mockPropertiesResultIterable = mockResultIterable();
        when(mockHandle.createQuery(contains("node_properties"))).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), (Object) any())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.bind(anyString(), anyInt())).thenReturn(mockPropertiesQuery);
        when(mockPropertiesQuery.map(ArgumentMatchers.<RowMapper<Object>>any()))
                .thenReturn(mockPropertiesResultIterable);
        when(mockPropertiesResultIterable.list()).thenReturn(List.of());

        final var result = repository.findAt(nodeId, timestamp);

        assertTrue(result.isPresent());
        verify(mockQuery).bind("id", nodeId.id());
        verify(mockQuery).bind("timestamp", timestamp.toString());
    }

    @Test
    @DisplayName("delete removes all versions of node")
    void testDeleteRemovesNode() {

        final var nodeId = NanoId.generate();
        when(mockUpdate.execute()).thenReturn(2);

        final var result = repository.delete(nodeId);

        assertTrue(result);
        verify(mockUpdate).bind("id", nodeId.id());
        verify(mockUpdate).execute();
    }

    @Test
    @DisplayName("expire updates active node with expiration timestamp")
    void testExpireUpdatesNode() {

        final var nodeId = NanoId.generate();
        final var expiredAt = Instant.now();
        when(mockUpdate.execute()).thenReturn(1);

        final var result = repository.expire(nodeId, expiredAt);

        assertTrue(result);
        verify(mockUpdate).bind("expired", expiredAt.toString());
        verify(mockUpdate).bind("id", nodeId.id());
        verify(mockUpdate).execute();
    }

    private static SimpleNode createTestNode() {

        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-value");
        return new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());
    }
}
