/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.persistence.mongodb.schemas.ComponentSchema;
import dev.iq.graph.persistence.mongodb.schemas.EdgeSchema;
import dev.iq.graph.persistence.mongodb.schemas.NodeSchema;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MongoGraphOperationsIntegrationTest {

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;
    private static MongoClient mongoClient;

    private MongoSession session;
    private MongoGraphRepository repository;
    private MongoGraphOperations graphOps;

    @BeforeEach
    void before() {
        // Start embedded MongoDB if not already started
        if (mongodProcess == null) {
            mongodProcess = MongoTestConfig.startMongoDbOrSkip();
            final var serverAddress = mongodProcess.current().getServerAddress();
            mongoClient = MongoClients.create("mongodb://" + serverAddress.getHost() + ':' + serverAddress.getPort());
        }

        session = new MongoSession(mongoClient, "test-db");

        // Create collections with schemas
        NodeSchema.createCollection(session.database());
        EdgeSchema.createCollection(session.database());
        ComponentSchema.createCollections(session.database());

        repository = MongoGraphRepository.create(session);
        graphOps = repository.graphOperations();
    }

    @AfterEach
    void after() {
        if (session != null) {
            session.database().drop();
            session.close();
        }
    }

    @AfterAll
    static void tearDownClass() {
        if (mongoClient != null) {
            mongoClient.close();
        }
        if (mongodProcess != null) {
            mongodProcess.close();
        }
    }

    @Test
    void testFindReachableNodes() {
        // Create a simple graph: A -> B -> C -> D
        final var nodeA = createAndSaveNode("A");
        final var nodeB = createAndSaveNode("B");
        final var nodeC = createAndSaveNode("C");
        final var nodeD = createAndSaveNode("D");

        createAndSaveEdge(nodeA, nodeB, "connects");
        createAndSaveEdge(nodeB, nodeC, "connects");
        createAndSaveEdge(nodeC, nodeD, "connects");

        // Find nodes reachable from A with max depth 2
        final var reachable = graphOps.findReachableNodes(nodeA.locator().id(), 2);

        assertEquals(3, reachable.size()); // A, B, C (not D due to depth limit)
        assertTrue(reachable.stream().anyMatch(n -> "A".equals(n.data().value())));
        assertTrue(reachable.stream().anyMatch(n -> "B".equals(n.data().value())));
        assertTrue(reachable.stream().anyMatch(n -> "C".equals(n.data().value())));
        assertFalse(reachable.stream().anyMatch(n -> "D".equals(n.data().value())));
    }

    @Test
    void testFindIncomingAndOutgoingEdges() {
        // Create a star pattern: A <- B -> C
        final var nodeA = createAndSaveNode("A");
        final var nodeB = createAndSaveNode("B");
        final var nodeC = createAndSaveNode("C");

        final var edgeBA = createAndSaveEdge(nodeB, nodeA, "points-to");
        final var edgeBC = createAndSaveEdge(nodeB, nodeC, "points-to");

        // Test incoming edges
        final var incomingToA = graphOps.findIncomingEdges(nodeA.locator().id());
        assertEquals(1, incomingToA.size());
        assertEquals(edgeBA.locator().id(), incomingToA.get(0).locator().id());

        final var incomingToB = graphOps.findIncomingEdges(nodeB.locator().id());
        assertEquals(0, incomingToB.size());

        // Test outgoing edges
        final var outgoingFromB = graphOps.findOutgoingEdges(nodeB.locator().id());
        assertEquals(2, outgoingFromB.size());
        assertTrue(outgoingFromB.stream()
                .anyMatch(e -> e.locator().id().equals(edgeBA.locator().id())));
        assertTrue(outgoingFromB.stream()
                .anyMatch(e -> e.locator().id().equals(edgeBC.locator().id())));
    }

    @Test
    void testFindNeighbors() {
        // Create a bidirectional connection: A <-> B -> C
        final var nodeA = createAndSaveNode("A");
        final var nodeB = createAndSaveNode("B");
        final var nodeC = createAndSaveNode("C");

        createAndSaveEdge(nodeA, nodeB, "connects");
        createAndSaveEdge(nodeB, nodeA, "connects");
        createAndSaveEdge(nodeB, nodeC, "connects");

        // B should have A and C as neighbors
        final var neighborsOfB = graphOps.findNeighbors(nodeB.locator().id());
        assertEquals(2, neighborsOfB.size());
        assertTrue(neighborsOfB.stream().anyMatch(n -> "A".equals(n.data().value())));
        assertTrue(neighborsOfB.stream().anyMatch(n -> "C".equals(n.data().value())));

        // A should have only B as neighbor
        final var neighborsOfA = graphOps.findNeighbors(nodeA.locator().id());
        assertEquals(1, neighborsOfA.size());
        assertTrue(neighborsOfA.stream().anyMatch(n -> "B".equals(n.data().value())));
    }

    @Test
    void testFindNodesWithinDistance() {
        // Create a chain: A -> B -> C -> D -> E
        final var nodeA = createAndSaveNode("A");
        final var nodeB = createAndSaveNode("B");
        final var nodeC = createAndSaveNode("C");
        final var nodeD = createAndSaveNode("D");
        final var nodeE = createAndSaveNode("E");

        createAndSaveEdge(nodeA, nodeB, "next");
        createAndSaveEdge(nodeB, nodeC, "next");
        createAndSaveEdge(nodeC, nodeD, "next");
        createAndSaveEdge(nodeD, nodeE, "next");

        // Find nodes within distance 2 from A
        final var nodesWithin2 =
                graphOps.findNodesWithinDistance(nodeA.locator().id(), 2);
        assertEquals(3, nodesWithin2.size()); // A, B, C
        assertTrue(nodesWithin2.contains(nodeA.locator().id()));
        assertTrue(nodesWithin2.contains(nodeB.locator().id()));
        assertTrue(nodesWithin2.contains(nodeC.locator().id()));
        assertFalse(nodesWithin2.contains(nodeD.locator().id()));
    }

    @Test
    void testPathExists() {
        // Create a diamond pattern: A -> B -> D, A -> C -> D
        final var nodeA = createAndSaveNode("A");
        final var nodeB = createAndSaveNode("B");
        final var nodeC = createAndSaveNode("C");
        final var nodeD = createAndSaveNode("D");
        final var nodeE = createAndSaveNode("E"); // Disconnected

        createAndSaveEdge(nodeA, nodeB, "path1");
        createAndSaveEdge(nodeB, nodeD, "path1");
        createAndSaveEdge(nodeA, nodeC, "path2");
        createAndSaveEdge(nodeC, nodeD, "path2");

        // Test paths
        assertTrue(graphOps.pathExists(nodeA.locator().id(), nodeD.locator().id()));
        assertTrue(graphOps.pathExists(nodeA.locator().id(), nodeB.locator().id()));
        assertFalse(graphOps.pathExists(nodeA.locator().id(), nodeE.locator().id()));
        assertFalse(graphOps.pathExists(nodeD.locator().id(), nodeA.locator().id())); // No reverse path
    }

    @Test
    void testExpiredElementsExcluded() {
        // Create nodes with one expired
        final var nodeA = createAndSaveNode("A");
        final var nodeB = createAndSaveNode("B");
        final var nodeC = createAndSaveNode("C");

        createAndSaveEdge(nodeA, nodeB, "connects");
        final var edgeBC = createAndSaveEdge(nodeB, nodeC, "connects");

        // Expire the B->C edge
        repository.edges().expire(edgeBC.locator().id(), Instant.now());

        // Node A should only reach B, not C
        final var reachable = graphOps.findReachableNodes(nodeA.locator().id(), 10);
        assertEquals(2, reachable.size()); // A and B only
        assertTrue(reachable.stream().anyMatch(n -> "A".equals(n.data().value())));
        assertTrue(reachable.stream().anyMatch(n -> "B".equals(n.data().value())));
        assertFalse(reachable.stream().anyMatch(n -> "C".equals(n.data().value())));

        // Path should not exist through expired edge
        assertFalse(graphOps.pathExists(nodeA.locator().id(), nodeC.locator().id()));
    }

    private SimpleNode createAndSaveNode(final String name) {
        final var id = NanoId.generate();
        final var node = TestDataHelper.createNode(id, 1, new TestData(name), Instant.now());
        return (SimpleNode) repository.nodes().save(node);
    }

    private SimpleEdge createAndSaveEdge(final SimpleNode source, final SimpleNode target, final String type) {
        final var id = NanoId.generate();
        final var edge = TestDataHelper.createEdge(id, 1, source, target, new TestData(type), Instant.now());
        return (SimpleEdge) repository.edges().save(edge);
    }

    // Test data class
    private record TestData(String name) implements Data {
        @Override
        public Class<?> javaClass() {
            return String.class;
        }

        @Override
        public Object value() {
            return name;
        }
    }
}
