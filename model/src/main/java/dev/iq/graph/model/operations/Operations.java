/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.common.version.Versioned;
import dev.iq.common.version.VersionedFinder;

/**
 * Common operations for versioned elements.
 * This interface provides read-only operations for querying versioned graph elements.
 */
public interface Operations<E extends Versioned> extends VersionedFinder<E> {
    // All operations are inherited from VersionedFinder
}
