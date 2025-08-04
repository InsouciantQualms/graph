/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Graph;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Graph interface and builder.
 */
@DisplayName("Graph Interface Tests")
public class GraphOperationsTest {

    private final Type defaultType = new SimpleType("test");

    @Test
    @DisplayName("Graph builder creates graph successfully")
    final void testGraphBuilderCreation() {
        // Test that we can create a graph using the builder
        final var builder = Graph.builder();
        assertNotNull(builder, "Graph builder should not be null");

        // Add at least one node to satisfy validation requirement
        final var nodeLocator = Locator.generate();
        builder.addNode(nodeLocator, defaultType, new TestData("Node1"), Instant.now());

        // Validate and build
        final var graph = builder.validate().build();
        assertNotNull(graph, "Built graph should not be null");

        // Verify we can access operations
        assertNotNull(graph.nodes(), "Node operations should not be null");
        assertNotNull(graph.edges(), "Edge operations should not be null");
        assertNotNull(graph.components(), "Component operations should not be null");
        assertNotNull(graph.paths(), "Path operations should not be null");
    }

    @Test
    @DisplayName("Graph provides access to all operation interfaces")
    final void testGraphOperationInterfaces() {
        final var builder = Graph.builder();

        // Add a node
        builder.addNode(Locator.generate(), defaultType, new TestData("Node1"), Instant.now());

        final var graph = builder.validate().build();

        // Test that all operation interfaces are accessible
        assertTrue(graph.nodes() instanceof NodeOperations);
        assertTrue(graph.edges() instanceof EdgeOperations);
        assertTrue(graph.components() instanceof ComponentOperations);
        assertTrue(graph.paths() instanceof PathOperations);
    }

    /**
     * Test data implementation.
     */
    private record TestData(String value) implements Data {
        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<?> javaClass() {
            return String.class;
        }
    }
}
