package dev.iq.graph.api;

import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Path;

import java.util.List;

/**
 * Service for performing graph-wide operations like path finding and connectivity analysis.
 */
public interface GraphService {

    /**
     * Checks if a path exists between two nodes in the active graph.
     */
    boolean hasPath(NanoId sourceNodeId, NanoId targetNodeId);

    /**
     * Returns all currently connected paths in the active graph.
     */
    List<Path> getActiveConnected();

    /**
     * Finds the shortest path between two nodes in the active graph.
     */
    Path getShortestPath(NanoId sourceNodeId, NanoId targetNodeId);
}
