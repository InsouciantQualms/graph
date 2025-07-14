package dev.iq.graph.persistence.tinkerpop;

import dev.iq.graph.persistence.GraphRepository;

/**
 * Graph listener repository using an in memory Tinkerpop implementation.
 */
public record TinkerpopGraphRepository(
    TinkerpopNodeRepository nodes,
    TinkerpopEdgeRepository edges,
    TinkerpopComponentRepository components
) implements GraphRepository {

    public static GraphRepository create(final TinkerpopSession session) {

        final var graph = session.graph();
        final var nodeRepository = new TinkerpopNodeRepository(graph);
        final var edgeRepository = new TinkerpopEdgeRepository(graph, nodeRepository);
        return new TinkerpopGraphRepository(
            nodeRepository,
            edgeRepository,
            new TinkerpopComponentRepository(graph, nodeRepository, edgeRepository)
        );
    }
}
