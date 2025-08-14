/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import java.time.Instant;
import java.util.Set;

/**
 * Builder interface for constructing graphs.
 * Provides methods to add, update, and expire nodes and edges.
 * This interface is primarily intended for testing and controlled graph construction.
 */
public interface GraphBuilder {

    /**
     * Add a new node to the graph.
     *
     * @param id the unique identifier for the node
     * @param type the type of the node
     * @param data the data associated with the node
     * @param timestamp the timestamp of creation
     * @return the created node
     */
    Node addNode(Uid id, Type type, Data data, Instant timestamp);

    /**
     * Update an existing node.
     *
     * @param id the unique identifier of the node to update
     * @param type the new type for the node
     * @param data the new data for the node
     * @param timestamp the timestamp of the update
     * @return the updated node
     */
    Node updateNode(Uid id, Type type, Data data, Instant timestamp);

    /**
     * Expire a node.
     *
     * @param id the unique identifier of the node to expire
     * @param timestamp the timestamp of expiration
     */
    void expireNode(Uid id, Instant timestamp);

    /**
     * Add a new edge to the graph.
     *
     * @param type the type of the edge
     * @param from the source node
     * @param to the target node
     * @param data the data associated with the edge
     * @param components the components this edge belongs to
     * @param timestamp the timestamp of creation
     * @return the created edge
     */
    Edge addEdge(Type type, Node from, Node to, Data data, Set<Locator> components, Instant timestamp);

    /**
     * Update an existing edge.
     *
     * @param id the unique identifier of the edge to update
     * @param type the new type for the edge
     * @param data the new data for the edge
     * @param components the new components this edge belongs to
     * @param timestamp the timestamp of the update
     * @return the updated edge
     */
    Edge updateEdge(Uid id, Type type, Data data, Set<Locator> components, Instant timestamp);

    /**
     * Expire an edge.
     *
     * @param id the unique identifier of the edge to expire
     * @param timestamp the timestamp of expiration
     */
    void expireEdge(Uid id, Instant timestamp);

    /**
     * Build and return the GraphSpace with all added elements.
     *
     * @return the constructed GraphSpace
     */
    GraphSpace build();
}
