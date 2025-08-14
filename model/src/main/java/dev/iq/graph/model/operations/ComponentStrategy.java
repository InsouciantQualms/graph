/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Strategy interface for component storage and retrieval.
 * Allows different implementations based on the underlying storage mechanism.
 */
public interface ComponentStrategy {

    /** Store a component. */
    void store(Component component);

    /** Find the active version of a component by ID. */
    Optional<Component> findActive(Uid id);

    /** Find all versions of a component. */
    List<Component> findVersions(Uid id);

    /** Get all active components. */
    List<Component> allActive();

    /** Update references when a component's locator changes. */
    void updateReferences(Locator oldLocator, Locator newLocator);

    /** Find all edges that reference a specific component. */
    Set<Edge> findEdgesReferencingComponent(Locator componentLocator);

    /** Clean up component data. */
    void clear();
}
