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
 * All elements in the path are fully loaded, but their internal references may be unloaded.
 */
public record Path(List<Reference<Element>> elements) {

    public Path(final List<Reference<Element>> elements) {

        Objects.requireNonNull(elements);
        if (!elements.isEmpty()) {
            Invariant.require(
                    elements.getFirst() instanceof Reference.Loaded<Element> loaded && loaded.value() instanceof Node,
                    "first element of a non‐empty path must be a loaded Node");
            Invariant.require(
                    elements.getLast() instanceof Reference.Loaded<Element> loaded && loaded.value() instanceof Node,
                    "last element of a non‐empty path must be a loaded Node");
        }
        this.elements = List.copyOf(elements);
    }
}
