/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api;

import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Node;
import java.util.List;

/**
 * Service for retrieving and manipiulating nodes (vertices) in the graph.
 */
public interface NodeService extends IdentifiableBase<Node> {

    /**
     * Adds a new node with the specified node data.
     */
    Node add(Data data);

    /**
     * Updates an existing node (creating a new version) with the specified data.  Updating a node will create a new version.
     */
    Node update(NanoId id, Data data);

    /**
     * Returns the neighbors of the specified node.
     */
    List<Node> getNeighbors(NanoId nodeId);
}
