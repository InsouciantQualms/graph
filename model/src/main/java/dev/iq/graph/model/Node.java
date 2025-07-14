package dev.iq.graph.model;

import dev.iq.common.adt.Stable;

import java.util.List;

/**
 * Represents a vertex in the graph that has zero or more edges (which can be incoming or outgoing from the node).
 */
@Stable
public interface Node extends Element {

    /**
     * Returns all the edges incoming to and outgoing from the node.
     */
    List<Edge> edges();
}
