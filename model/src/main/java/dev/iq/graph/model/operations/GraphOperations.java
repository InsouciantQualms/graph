/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.Node;
import java.time.Instant;
import java.util.Collection;

/**
 * Operations for creating graph elements.
 * This interface provides factory methods for creating nodes, edges, and components.
 */
public interface GraphOperations {

    /**
     * Adds a new node to the graph.
     */
    Node addNode(Data data, Instant timestamp);

    /**
     * Adds a new edge to the graph.
     */
    Edge addEdge(Node source, Node target, Data data, Instant timestamp);

    /**
     * Adds a new component to the graph.
     */
    Component addComponent(Collection<Element> elements, Data data, Instant timestamp);
}
