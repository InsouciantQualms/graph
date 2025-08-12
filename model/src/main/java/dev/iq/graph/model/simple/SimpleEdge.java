/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.simple;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Record implementation of Edge.
 */
public record SimpleEdge(
        Locator locator,
        Type type,
        Node source,
        Node target,
        Data data,
        Set<Locator> components,
        Instant created,
        Optional<Instant> expired)
        implements Edge {

    @SuppressWarnings("ConstructorWithTooManyParameters")
    public SimpleEdge {
        components = Set.copyOf(components);
    }

    /** {@inheritDoc} */
    @Override
    public Edge expire(final Instant timestamp) {

        return new SimpleEdge(locator, type, source, target, data, components, created, Optional.of(timestamp));
    }
}
