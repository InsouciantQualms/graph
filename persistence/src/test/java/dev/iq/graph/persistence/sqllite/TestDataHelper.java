/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.sqllite;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.simple.SimpleEdge;
import dev.iq.graph.model.simple.SimpleNode;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Helper class for creating test data objects.
 */
final class TestDataHelper {

    private TestDataHelper() {}

    public static Node createNode(final Uid id, final int version, final Data data, final Instant created) {

        final var locator = new Locator(id, version);
        final var type = new SimpleType("test");
        return new SimpleNode(locator, type, data, created, Optional.empty());
    }

    public static Node createExpiredNode(
            final Uid id, final int version, final Data data, final Instant created, final Instant expired) {

        final var locator = new Locator(id, version);
        final var type = new SimpleType("test");
        return new SimpleNode(locator, type, data, created, Optional.of(expired));
    }

    public static Edge createEdge(
            final Uid id,
            final int version,
            final Node source,
            final Node target,
            final Data data,
            final Instant created) {

        final var locator = new Locator(id, version);
        final var type = new SimpleType("test");
        final Set<Locator> components = new HashSet<>();
        return new SimpleEdge(locator, type, source, target, data, components, created, Optional.empty());
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    public static Edge createExpiredEdge(
            final Uid id,
            final int version,
            final Node source,
            final Node target,
            final Data data,
            final Instant created,
            final Instant expired) {

        final var locator = new Locator(id, version);
        final var type = new SimpleType("test");
        final Set<Locator> components = new HashSet<>();
        return new SimpleEdge(locator, type, source, target, data, components, created, Optional.of(expired));
    }
}
