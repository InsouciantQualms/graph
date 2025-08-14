/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.UidFactory;
import dev.iq.graph.model.jgrapht.JGraphtGraphBuilder;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for GraphSpace interface and its implementation.
 */
@DisplayName("GraphSpace Tests")
class GraphSpaceTest {

    private GraphBuilder graphBuilder;
    private GraphSpace graphSpace;
    private final Type nodeType = new SimpleType("node");
    private final Type edgeType = new SimpleType("edge");
    private final Type componentType = new SimpleType("component");
    private final Instant timestamp = Instant.now();

    @BeforeEach
    final void before() {
        graphBuilder = new JGraphtGraphBuilder();
        graphSpace = graphBuilder.build();
    }

    @Test
    @DisplayName("GraphSpace extends Space and provides view")
    final void testGraphSpaceExtendsSpace() {
        // GraphSpace should extend Space
        assertTrue(graphSpace instanceof Space);

        // Should provide access to View
        final var view = graphSpace.view();
        assertNotNull(view);
    }

    @Test
    @DisplayName("GraphSpace components() returns VersionedFinder")
    final void testGraphSpaceComponents() {
        final var components = graphSpace.components();
        assertNotNull(components);
    }

    @Test
    @DisplayName("ComponentSpace not found for non-existent component")
    final void testComponentSpaceNotFound() {
        final Uid nonExistentId = UidFactory.generate();
        final var foundById = graphSpace.componentSpace(nonExistentId);
        assertFalse(foundById.isPresent());

        final var nonExistentLocator = Locator.generate();
        final var foundByLocator = graphSpace.componentSpace(nonExistentLocator);
        assertFalse(foundByLocator.isPresent());
    }
}
