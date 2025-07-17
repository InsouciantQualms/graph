/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.simple;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public record SimpleEdge(
        Locator locator,
        Node source,
        Node target,
        Data data,
        Instant created,
        Optional<Instant> expired,
        Set<NanoId> components)
        implements Edge {

    /**
     * Constructor that initializes components to empty set.
     */
    public SimpleEdge(
            final Locator locator,
            final Node source,
            final Node target,
            final Data data,
            final Instant created,
            final Optional<Instant> expired) {

        this(locator, source, target, data, created, expired, new HashSet<>());
    }
}
