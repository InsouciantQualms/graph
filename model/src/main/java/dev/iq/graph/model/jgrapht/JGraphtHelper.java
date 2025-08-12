/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Uid;
import dev.iq.common.version.Versioned;
import java.util.Optional;

/**
 * Helper utilities for graph operations.
 */
final class JGraphtHelper {

    /** Type contains only static members. */
    private JGraphtHelper() {}

    /** Returns an element if it exists, otherwise throws an exception. */
    public static <E extends Versioned> E require(final Optional<E> element, final Uid id, final String elementType) {

        return element.orElseThrow(() -> new IllegalArgumentException(elementType + " not found: " + id));
    }
}
