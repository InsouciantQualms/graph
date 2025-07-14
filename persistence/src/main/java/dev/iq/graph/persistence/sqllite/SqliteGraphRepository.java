package dev.iq.graph.persistence.sqllite;

import dev.iq.graph.persistence.GraphRepository;

/**
 * Graph listener repository using an in memory SQLite implementation.
 */
public record SqliteGraphRepository(
    SqliteNodeRepository nodes,
    SqliteEdgeRepository edges,
    SqliteComponentRepository components
) implements GraphRepository {

    public static GraphRepository create(final SqliteSession session) {

        final var nodeRepository = new SqliteNodeRepository(session);
        final var edgeRepository = new SqliteEdgeRepository(session, nodeRepository);
        return new SqliteGraphRepository(
            nodeRepository,
            edgeRepository,
            new SqliteComponentRepository(session, nodeRepository, edgeRepository)
        );
    }
}
