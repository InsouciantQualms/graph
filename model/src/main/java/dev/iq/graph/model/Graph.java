/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.operations.NodeOperations;
import dev.iq.graph.model.operations.PathOperations;
import java.util.Set;

/**
 * Encapsulates a session-based graph that can have queries and operations performed.
 * Graph serves as a container/boundary for all graph elements and operations.
 * Implementations must be thread-safe.
 */
public interface Graph extends AutoCloseable {

    /**
     * Returns operations for managing nodes in this graph.
     */
    NodeOperations nodes();

    /**
     * Returns operations for managing edges in this graph.
     */
    EdgeOperations edges();

    /**
     * Returns operations for managing components associated with this graph.
     */
    ComponentOperations components();

    /**
     * Returns operations for finding paths in this graph.
     */
    PathOperations paths();

    /**
     * Returns a complete view of this graph.
     */
    GraphView asView();

    /**
     * Returns a filtered view of this graph containing only the specified nodes and edges.
     */
    GraphView asView(Set<Node> nodes, Set<Edge> edges);

    /**
     * Creates a new empty graph builder.
     */
    static GraphBuilder builder() {
        // Implementation will be provided by concrete implementations
        return new dev.iq.graph.model.jgrapht.JGraphtGraphBuilder();
    }

    /**
     * Creates a builder initialized with the current graph's contents.
     * This allows for creating modified versions of existing graphs.
     */
    GraphBuilder toBuilder();
}
