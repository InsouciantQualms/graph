/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import dev.iq.common.version.NanoId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MongoGraphOperations construction and basic validation.
 * For comprehensive functionality tests, see MongoGraphOperationsIntegrationTest.
 */
class MongoGraphOperationsTest {

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;
    private static MongoClient mongoClient;

    private MongoSession session;
    private MongoGraphRepository repository;
    private MongoGraphOperations graphOperations;

    @BeforeEach
    final void before() {
        // Start embedded MongoDB if not already started
        if (mongodProcess == null) {
            mongodProcess = MongoTestConfig.startMongoDbOrSkip();
            final var serverAddress = mongodProcess.current().getServerAddress();
            mongoClient = MongoClients.create("mongodb://" + serverAddress.getHost() + ':' + serverAddress.getPort());
        }

        session = new MongoSession(mongoClient, "test-db");
        repository = MongoGraphRepository.create(session);
        graphOperations = repository.graphOperations();
    }

    @AfterEach
    final void after() {
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
    final void testConstructorCreatesValidInstance() {
        assertNotNull(graphOperations);
    }

    @Test
    final void testOperationsWithNonExistentNodes() {
        final var nonExistentId = NanoId.generate();

        // These should not throw exceptions even with non-existent IDs
        assertNotNull(graphOperations.findReachableNodes(nonExistentId, 3));
        assertNotNull(graphOperations.findIncomingEdges(nonExistentId));
        assertNotNull(graphOperations.findOutgoingEdges(nonExistentId));
        assertNotNull(graphOperations.findNeighbors(nonExistentId));
        assertNotNull(graphOperations.findNodesWithinDistance(nonExistentId, 2));
    }
}
