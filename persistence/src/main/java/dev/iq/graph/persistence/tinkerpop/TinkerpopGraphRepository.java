/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.graph.persistence.GraphRepository;
import org.springframework.stereotype.Repository;

/**
 * Graph listener repository using an in memory Tinkerpop implementation.
 */
@Repository("tinkerpopGraphRepository")
public record TinkerpopGraphRepository(
        TinkerpopNodeRepository nodes, TinkerpopEdgeRepository edges, TinkerpopComponentRepository components)
        implements GraphRepository {

    public static GraphRepository create(final TinkerpopSession session) {

        final var graph = session.graph();
        final var nodeRepository = new TinkerpopNodeRepository(graph);
        final var edgeRepository = new TinkerpopEdgeRepository(graph, nodeRepository);
        return new TinkerpopGraphRepository(
                nodeRepository,
                edgeRepository,
                new TinkerpopComponentRepository(graph, nodeRepository, edgeRepository));
    }
}
