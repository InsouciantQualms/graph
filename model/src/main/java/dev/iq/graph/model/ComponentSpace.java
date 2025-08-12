/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.Uid;
import java.util.Set;

/**
 * Operations for managing components in a graph.
 */
public interface ComponentSpace {

    /** Returns a view of all elemeents in the specified component. */
    View view(Uid id);

    /** Gets all components containing the specified element (node or edge). */
    Set<Component> componentsForElement(Uid id);
}
