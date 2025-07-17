/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api;

import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import java.time.Instant;
import java.util.List;

/**
 * Services for retrieving and manipulating a component.  Components conceptually are
 * maximal sub-graphs containing nodes and elements.  Each component is identified by
 * a Nano ID and are versioned like other graph elements.  Components can overlap
 * other components.
 * Implementations must be thread-safe.
 */
public interface ComponentService extends IdentifiableBase<Component> {

    /**
     * Adds or defines a new component with the specified list of elements.  All elements must exist and be active.
     */
    Component add(List<Element> elements, Data data);

    /**
     * Updates an existing component (creating a new version) with the specified list of elements.  All elements must exist and be active.
     */
    Component update(NanoId id, List<Element> elements, Data data);

    /**
     * Finds all active components that contain the specified element (node or edge) ID.
     */
    List<Component> findActiveContaining(NanoId id);

    /**
     * Finds all components that contain the specified element (node or edge) at the specified timestamp (active or expired).
     */
    List<Component> findContaining(NanoId id, Instant timestamp);
}
