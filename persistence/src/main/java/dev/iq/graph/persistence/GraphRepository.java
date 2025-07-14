package dev.iq.graph.persistence;

import dev.iq.common.adt.Stable;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;

/**
 * Common interface for graph persistence operations.
 * Provides CRUD operations for nodes and edges with version support.
 */
@Stable
public interface GraphRepository {

    VersionedRepository<Node> nodes();

    VersionedRepository<Edge> edges();

    VersionedRepository<Component> components();
}
