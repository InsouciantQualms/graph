/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versioned;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interface defining common behavior across Node, Edge and Component services.
 * This interface is not meant to be used directly.  Please see insetad
 * NodeService, EdgeService and ComponentService.
 */
interface IdentifiableBase<T extends Versioned> {

    /**
     * Returns the exact item specified by an ID and version (active or inactive).  If it does not exist, an exception will be thrown.
     */
    T find(Locator locator);

    /**
     * Returns the active (most current) version of the specified ID.
     */
    Optional<T> findActive(NanoId id);

    /**
     * Returns the version of the specpfied ID that existed at the specified timestamp (active or inactive).
     */
    Optional<T> findAt(NanoId id, Instant timestamp);

    /**
     * Returns all versions available for the specified ID.
     */
    List<T> findAllVersions(NanoId id);

    /**
     * Returns all IDs that are active in the entire graph.
     */
    List<NanoId> allActive();

    /**
     * Returns all IDs that exist in the entire graph (active or inactive).
     */
    List<NanoId> all();

    /**
     * Expires (makes inactive) the specified ID.  This also serves a a logical delete.  The expired entry is returned if the ID existed.
     */
    Optional<T> expire(NanoId id);

    /**
     * Fully and permanently deletes the specified ID from the graph.  True is returned if the item existed previously.
     */
    boolean delete(NanoId id);
}
