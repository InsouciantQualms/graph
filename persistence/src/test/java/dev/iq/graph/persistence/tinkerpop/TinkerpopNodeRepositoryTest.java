package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleNode;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TinkerpopNodeRepository.
 */
@DisplayName("TinkerpopNodeRepository Unit Tests")
final class TinkerpopNodeRepositoryTest {

    private Graph graph;
    private GraphTraversalSource g;
    private TinkerpopNodeRepository repository;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        g = graph.traversal();
        repository = new TinkerpopNodeRepository(graph);
    }

    @Test
    @DisplayName("save persists node in graph")
    void testSavePersistsNode() {
        
        final var node = createTestNode();
        
        final var result = repository.save(node);
        
        // Verify node was saved
        assertEquals(node, result);
        
        // Verify node exists in graph
        final var vertices = g.V().hasLabel("node")
            .has("id", node.locator().id().id())
            .has("versionId", node.locator().version())
            .toList();
        
        assertEquals(1, vertices.size());
    }

    @Test
    @DisplayName("findActive returns active node")
    void testFindActiveReturnsNode() {
        
        final var nodeId = new NanoId("test-node");
        final var node = new SimpleNode(
            new Locator(nodeId, 1),
            List.of(),
            new SimpleData(String.class, "test-value"),
            Instant.now(),
            Optional.empty()
        );
        
        repository.save(node);
        
        final var result = repository.findActive(nodeId);
        
        assertTrue(result.isPresent());
        assertEquals(nodeId, result.get().locator().id());
        assertEquals(1, result.get().locator().version());
        assertFalse(result.get().expired().isPresent());
    }

    @Test
    @DisplayName("findActive returns empty when no active node exists")
    void testFindActiveReturnsEmpty() {
        
        final var nodeId = new NanoId("test-node");
        final var expiredNode = new SimpleNode(
            new Locator(nodeId, 1),
            List.of(),
            new SimpleData(String.class, "test-value"),
            Instant.now(),
            Optional.of(Instant.now())
        );
        
        repository.save(expiredNode);
        
        final var result = repository.findActive(nodeId);
        
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findAll returns all versions of a node")
    void testFindAllReturnsAllVersions() {
        
        final var nodeId = new NanoId("test-node");
        final var node1 = new SimpleNode(
            new Locator(nodeId, 1),
            List.of(),
            new SimpleData(String.class, "test-value-1"),
            Instant.now(),
            Optional.of(Instant.now())
        );
        final var node2 = new SimpleNode(
            new Locator(nodeId, 2),
            List.of(),
            new SimpleData(String.class, "test-value-2"),
            Instant.now(),
            Optional.empty()
        );
        
        repository.save(node1);
        repository.save(node2);
        
        final var results = repository.findAll(nodeId);
        
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).locator().version());
        assertEquals(2, results.get(1).locator().version());
    }

    @Test
    @DisplayName("find returns specific version of node")
    void testFindReturnsSpecificVersion() {
        
        final var locator = new Locator(NanoId.generate(), 2);
        final var node = new SimpleNode(
            locator,
            List.of(),
            new SimpleData(String.class, "test-value"),
            Instant.now(),
            Optional.empty()
        );
        
        repository.save(node);
        
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
        
        final var node = new SimpleNode(
            new Locator(nodeId, 1),
            List.of(),
            new SimpleData(String.class, "test-value"),
            created,
            Optional.empty()
        );
        
        repository.save(node);
        
        final var result = repository.findAt(nodeId, timestamp);
        
        assertTrue(result.isPresent());
        assertEquals(nodeId, result.get().locator().id());
    }

    @Test
    @DisplayName("delete removes all versions of node")
    void testDeleteRemovesNode() {
        
        final var nodeId = NanoId.generate();
        final var node = new SimpleNode(
            new Locator(nodeId, 1),
            List.of(),
            new SimpleData(String.class, "test-value"),
            Instant.now(),
            Optional.empty()
        );
        
        repository.save(node);
        
        final var result = repository.delete(nodeId);
        
        assertTrue(result);
        
        // Verify node is gone
        final var vertices = g.V().hasLabel("node")
            .has("id", nodeId.id())
            .toList();
        
        assertTrue(vertices.isEmpty());
    }

    @Test
    @DisplayName("expire updates active node with expiration timestamp")
    void testExpireUpdatesNode() {
        
        final var nodeId = NanoId.generate();
        final var node = new SimpleNode(
            new Locator(nodeId, 1),
            List.of(),
            new SimpleData(String.class, "test-value"),
            Instant.now(),
            Optional.empty()
        );
        
        repository.save(node);
        
        final var expiredAt = Instant.now();
        final var result = repository.expire(nodeId, expiredAt);
        
        assertTrue(result);
        
        // Verify node is expired
        final var foundNode = repository.find(new Locator(nodeId, 1));
        assertTrue(foundNode.isPresent());
        assertTrue(foundNode.get().expired().isPresent());
    }

    private SimpleNode createTestNode() {
        
        final var locator = new Locator(NanoId.generate(), 1);
        final var data = new SimpleData(String.class, "test-value");
        return new SimpleNode(locator, List.of(), data, Instant.now(), Optional.empty());
    }
}