/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import java.util.List;
import java.util.Optional;

/**
 * Operations for finding paths in a graph.
 */
public interface PathOperations {

    /** Determines if any path exists between the two nodes. */
    boolean pathExists(Node source, Node target);

    /** Finds the shortest path between two nodes. */
    Optional<Path> findShortestPath(Node source, Node target);

    /** Finds all paths between two nodes. */
    List<Path> findAllPaths(Node source, Node target);
}
