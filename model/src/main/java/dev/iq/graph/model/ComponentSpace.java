/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.Uid;
import java.util.Set;

/**
 * Represents a subgraph view for a single component.
 * Provides access to the component and its elements.
 */
public interface ComponentSpace extends Space {

    /** The component this space represents. */
    Component component();

    /** Get all edges that define this component. */
    Set<Edge> componentEdges();

    /** Get all nodes within this component (derived from edges). */
    Set<Node> componentNodes();

    /** Gets all components containing the specified element (node or edge). */
    Set<Component> componentsForElement(Uid id);
}
