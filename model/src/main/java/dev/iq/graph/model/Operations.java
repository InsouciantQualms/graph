/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versioned;

/**
 * Common operations for versioned elements.
 */
public interface Operations<E extends Versioned> {

    /**
     * Finds the active version of an element by ID.
     */
    Optional<E> findActive(NanoId id);

    /**
     * Finds the version of an element active at a specific timestamp.
     */
    Optional<E> findAt(NanoId id, Instant timestamp);

    /**
     * Finds all versions of an element by ID.
     */
    List<E> findAllVersions(NanoId id);

    /**
     * Returns all active elements.
     */
    List<E> allActive();

    /**
     * Expires an element at the given timestamp.
     */
    E expire(NanoId id, Instant timestamp);
}
