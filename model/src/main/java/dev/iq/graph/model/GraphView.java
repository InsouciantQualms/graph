/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import java.util.Set;

/**
 * A view of a graph that provides access to nodes and edges without exposing implementation details.
 * This abstraction allows switching between different graph implementations (JGraphT, Guava, etc.)
 * without affecting client code.
 */
public interface GraphView {

    /**
     * Returns all nodes in this graph view.
     */
    Set<Node> nodes();

    /**
     * Returns all edges in this graph view.
     */
    Set<Edge> edges();

    /**
     * Returns all outgoing edges from the specified node.
     */
    Set<Edge> outgoingEdges(Node node);

    /**
     * Returns all incoming edges to the specified node.
     */
    Set<Edge> incomingEdges(Node node);

    /**
     * Returns the source node of the specified edge.
     */
    Node source(Edge edge);

    /**
     * Returns the target node of the specified edge.
     */
    Node target(Edge edge);

    /**
     * Checks if this graph view contains the specified node.
     */
    boolean contains(Node node);

    /**
     * Checks if this graph view contains the specified edge.
     */
    boolean contains(Edge edge);
}
