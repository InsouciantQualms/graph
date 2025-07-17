/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.simple;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;

public record SimpleNode(
        Locator locator,
        List<Edge> edges,
        Data data,
        Instant created,
        Optional<Instant> expired,
        Set<NanoId> components)
        implements Node {

    /**
     * Constructor that initializes components to empty set.
     */
    public SimpleNode(
            final Locator locator,
            final List<Edge> edges,
            final Data data,
            final Instant created,
            final Optional<Instant> expired) {

        this(locator, edges, data, created, expired, new HashSet<>());
    }
}
