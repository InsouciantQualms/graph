package dev.iq.graph.persistence.mongodb;

import dev.iq.graph.persistence.GraphRepository;

/**
 * MongoDB implementation of GraphRepository compatible with DelegatedGraphListenerRepository.
 */
public record MongoGraphRepository(
    MongoNodeRepository nodes,
    MongoEdgeRepository edges,
    MongoComponentRepository components
) implements GraphRepository {

    public static MongoGraphRepository create(final MongoSession session) {

        final var nodeRepository = new MongoNodeRepository(session.database());
        final var edgeRepository = new MongoEdgeRepository(session.database(), nodeRepository);
        return new MongoGraphRepository(
            nodeRepository,
            edgeRepository,
            new MongoComponentRepository(session.database(), nodeRepository, edgeRepository)
        );
    }
}
