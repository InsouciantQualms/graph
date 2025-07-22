/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.graph.persistence.AbstractGraphListenerReferentialIntegrityIntegrationTest;
import dev.iq.graph.persistence.GraphRepository;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.DisplayName;

/**
 * Integration tests for TinkerpopGraphRepository to validate
 * referential integrity when using graph listeners.
 */
@DisplayName("Tinkerpop Graph Listener Referential Integrity Integration Tests")
final class TinkerpopGraphListenerReferentialIntegrityIntegrationTest
        extends AbstractGraphListenerReferentialIntegrityIntegrationTest {

    @Override
    protected GraphRepository createGraphRepository() {

        final var graph = TinkerGraph.open();
        final var nodeRepository = new TinkerpopNodeRepository(graph);
        final var edgeRepository = new TinkerpopEdgeRepository(graph, nodeRepository);
        final var componentRepository = new TinkerpopComponentRepository(graph, nodeRepository, edgeRepository);
        return new TinkerpopGraphRepository(nodeRepository, edgeRepository, componentRepository);
    }
}
