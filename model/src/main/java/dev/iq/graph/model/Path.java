/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.error.Invariant;
import java.util.List;
import java.util.Objects;

/**
 * Represents a simple path between elements.  The first and last elements of the
 * path will always be a Node instance.  The path can contain zero or more elements.
 */
public record Path(List<Element> elements) {

    public Path(final List<Element> elements) {

        Objects.requireNonNull(elements);
        if (!elements.isEmpty()) {
            Invariant.requireTrue(
                    elements.getFirst() instanceof Node, "first element of a non‐empty path must be a Node");
            Invariant.requireTrue(
                    elements.getLast() instanceof Node, "last element of a non‐empty path must be a Node");
        }
        this.elements = List.copyOf(elements);
    }
}
