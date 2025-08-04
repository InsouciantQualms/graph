/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import java.util.Set;

/**
 * Operations for managing edges in a graph.
 */
public interface EdgeOperations extends Operations<Edge> {

    /**
     * Gets the source node of the specified edge.
     */
    Node source(Edge edge);

    /**
     * Gets the target node of the specified edge.
     */
    Node target(Edge edge);

    /**
     * Gets all components that contain the specified edge.
     */
    Set<Component> components(Edge edge);
}
