/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.VersionedFinder;

/**
 * Encapsulates a session-based graph that can have queries and operations performed.
 * GraphSpace serves as a container/boundary for all graph elements and operations.
 * Implementations must be thread-safe.
 */
public interface GraphSpace {

    /** Returns operations for managing nodes and edges in this graph. */
    View view();

    /** Returns operations for managing components associated with this graph. */
    VersionedFinder<Component> components();
}
