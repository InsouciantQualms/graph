/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versioned;
import dev.iq.common.version.VersionedFinder;
import java.time.Instant;

/**
 * Common operations for versioned elements.
 */
public interface Operations<E extends Versioned> extends VersionedFinder<E> {

    /**
     * Expires an element at the given timestamp.
     */
    E expire(NanoId id, Instant timestamp);
}
