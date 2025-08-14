/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.VersionedFinder;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates a session-based graph that can have queries and operations performed.
 * GraphSpace serves as a container/boundary for all graph elements and operations.
 * Implementations must be thread-safe.
 */
public interface GraphSpace extends Space {

    /** Returns operations for managing components associated with this graph. */
    VersionedFinder<Component> components();

    /** Get a ComponentSpace for a specific component. */
    Optional<ComponentSpace> componentSpace(Uid componentId);

    /** Get a ComponentSpace for a specific component version. */
    Optional<ComponentSpace> componentSpace(Locator componentLocator);

    /** Find all ComponentSpaces that contain a given element. */
    Set<ComponentSpace> componentSpacesContaining(Element element);

    /** Create a new component from a set of edges. */
    ComponentSpace createComponent(Set<Edge> edges, Type type, Data data, Instant timestamp);
}
