package dev.iq.graph.persistence.mongodb;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Integration test for MongoDB graph repository that verifies referential integrity is maintained.
 */
final class MongoGraphListenerReferentialIntegrityIntegrationTest
    extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongodProcess;

    @BeforeAll
    static void setUpClass() {

        mongodProcess = Mongod.instance().start(Version.Main.V7_0);
    }

    @AfterAll
    static void tearDownClass() {

        if (mongodProcess != null) {
            mongodProcess.close();
        }
    }

    @Override
    protected GraphRepository createGraphRepository() {

        final var serverAddress = mongodProcess.current().getServerAddress();
        final var uri = "mongodb://" + serverAddress.getHost() + ':' + serverAddress.getPort();
        final var factory = new MongoSessionFactory(uri, "test_graph_integration");
        try (final var session = (MongoSession) factory.create()) {
            return MongoGraphRepository.create(session);
        }
    }
}