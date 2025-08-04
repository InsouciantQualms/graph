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
 * Operations for managing nodes in a graph.
 */
public interface NodeOperations extends Operations<Node> {

    /**
     * Gets all outgoing edges from the specified node.
     */
    Set<Edge> outgoingEdges(Node node);

    /**
     * Gets all incoming edges to the specified node.
     */
    Set<Edge> incomingEdges(Node node);

    /**
     * Gets all edges connected to the specified node (both incoming and outgoing).
     */
    Set<Edge> edges(Node node);

    /**
     * Gets all components that contain the specified node.
     */
    Set<Component> components(Node node);

    /**
     * Gets all neighbor nodes connected to the specified node.
     */
    Set<Node> neighbors(Node node);
}
