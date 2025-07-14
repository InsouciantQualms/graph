package dev.iq.graph.api;

import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;

import java.util.List;

/**
 * Service for retrieving and manipulating edges in the graph.  Within the graph,
 * two nodes can contain multiple, parallel edges.
 */
public interface EdgeService extends IdentifiableBase<Edge> {

    /**
     * Creates a new edge between the specified nodes and with the supplied data.
     */
    Edge addEdge(Node source, Node target, Data data);

    /**
     * Updates the specified edget with new data, creating a new version.
     */
    Edge updateEdge(NanoId id, Data data);

    /**
     * Returns the edges from the specified node.
     */
    List<Edge> getEdgesFrom(NanoId nodeId);

    /**
     * Returns the edges to the specified node.
     */
    List<Edge> getEdgesTo(NanoId nodeId);
}
