/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Integration test for MongoDB graph repository that verifies referential integrity is maintained.
 */
final class MongoGraphListenerReferentialIntegrityIntegrationTest
        extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;
    private static MongoClient mongoClient;
    private static final String TEST_DB = "test_graph_integration";

    @BeforeAll
    static void setUpClass() {

        mongodProcess = Mongod.instance().start(Version.Main.V7_0);
        final var serverAddress = mongodProcess.current().getServerAddress();
        final var uri = "mongodb://" + serverAddress.getHost() + ':' + serverAddress.getPort();
        mongoClient = MongoClients.create(uri);
    }

    @BeforeEach
    void beforeEach() {
        // Clean up collections before each test
        final var database = mongoClient.getDatabase(TEST_DB);
        database.getCollection("nodes").drop();
        database.getCollection("edges").drop();
        database.getCollection("components").drop();
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

    @Override
    protected GraphRepository createGraphRepository() {

        final var session = new MongoSession(mongoClient, TEST_DB);
        return MongoGraphRepository.create(session);
    }
}
