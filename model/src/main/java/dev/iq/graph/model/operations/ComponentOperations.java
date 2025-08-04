/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.GraphView;
import dev.iq.graph.model.Node;
import java.util.Set;

/**
 * Operations for managing components in a graph.
 */
public interface ComponentOperations extends Operations<Component> {

    /**
     * Returns a graph view representing this component.
     */
    GraphView asGraph(Component component);

    /**
     * Gets all nodes in the specified component.
     */
    Set<Node> nodes(Component component);

    /**
     * Gets all edges in the specified component.
     */
    Set<Edge> edges(Component component);

    /**
     * Checks if the specified element is contained in the component.
     */
    boolean contains(Component component, Element element);
}
