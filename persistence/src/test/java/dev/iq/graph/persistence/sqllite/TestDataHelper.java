/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;

/**
 * Helper class for creating test data objects.
 */
final class TestDataHelper {

    private TestDataHelper() {}

    public static Node createNode(final NanoId id, final int version, final Data data, final Instant created) {

        final var locator = new Locator(id, version);
        return new SimpleNode(locator, List.of(), data, created, Optional.empty());
    }

    public static Node createExpiredNode(
            final NanoId id, final int version, final Data data, final Instant created, final Instant expired) {

        final var locator = new Locator(id, version);
        return new SimpleNode(locator, List.of(), data, created, Optional.of(expired));
    }

    public static Edge createEdge(
            final NanoId id,
            final int version,
            final Node source,
            final Node target,
            final Data data,
            final Instant created) {

        final var locator = new Locator(id, version);
        return new SimpleEdge(locator, source, target, data, created, Optional.empty());
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    public static Edge createExpiredEdge(
            final NanoId id,
            final int version,
            final Node source,
            final Node target,
            final Data data,
            final Instant created,
            final Instant expired) {

        final var locator = new Locator(id, version);
        return new SimpleEdge(locator, source, target, data, created, Optional.of(expired));
    }
}
