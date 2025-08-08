/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.common.annotation.Stable;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Type;
import java.time.Instant;

/**
 * Mutable operations for managing components in a graph during construction or bulk operations.
 * These operations are separate from the immutable ComponentSpace interface as they serve
 * a different purpose - graph construction rather than graph querying.
 *
 * When a component is updated, all elements referencing it need to be updated to maintain
 * referential integrity with the new component version.
 */
@Stable
public interface ComponentOperations {

    /** Adds a new component. */
    Component add(Type type, Data data, Instant timestamp);

    /** Updates an existing component.  This will expire the current version and create a new version. */
    Component update(NanoId id, Type type, Data data, Instant timestamp);

    /** Expires a component at the given timestamp. */
    Component expire(NanoId id, Instant timestamp);
}
