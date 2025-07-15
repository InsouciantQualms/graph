/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */
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
