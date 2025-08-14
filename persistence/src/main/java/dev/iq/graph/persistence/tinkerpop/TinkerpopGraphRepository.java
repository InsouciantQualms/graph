/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.graph.persistence.GraphRepository;
import org.springframework.stereotype.Repository;

/**
 * GraphSpace listener repository using an in memory Tinkerpop implementation.
 *
 * Note: TinkerPop implementations should use GraphComponentStrategy for component storage,
 * which stores components as special manifest nodes in the graph itself. This is different
 * from MongoDB and SQLite implementations which use SeparateComponentStrategy.
 */
@Repository("tinkerpopGraphRepository")
public record TinkerpopGraphRepository(
        TinkerpopNodeRepository nodes, TinkerpopEdgeRepository edges, TinkerpopComponentRepository components)
        implements GraphRepository {

    public static GraphRepository create(final TinkerpopSession session) {

        final var graph = session.graph();
        final var nodeRepository = new TinkerpopNodeRepository(graph);
        final var edgeRepository = new TinkerpopEdgeRepository(graph, nodeRepository);
        final var componentRepository = new TinkerpopComponentRepository(graph, nodeRepository, edgeRepository);
        return new TinkerpopGraphRepository(nodeRepository, edgeRepository, componentRepository);
    }
}
