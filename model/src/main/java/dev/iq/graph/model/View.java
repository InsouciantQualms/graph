package dev.iq.graph.model;

import dev.iq.common.version.VersionedFinder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable view of the graph or a sub-graph (component).
 */
public interface View {

    /** Finder to locate nodes. */
    VersionedFinder<Node> nodes();

    /** Finder to locate edges. */
    VersionedFinder<Edge> edges();

    /** Gets all neighbor nodes connected to the specified node. */
    Set<Node> neighbors(Node node);

    /** Gets all outgoing edges from the specified node. */
    Set<Edge> outgoingEdges(Node node);

    /** Gets all incoming edges to the specified node. */
    Set<Edge> incomingEdges(Node node);

    /** Determines if any path exists between the two nodes. */
    boolean pathExists(Node source, Node target);

    /** Finds the shortest path between two nodes. */
    Optional<Path> findShortestPath(Node source, Node target);

    /** Finds all paths between two nodes. */
    List<Path> findAllPaths(Node source, Node target);
}
