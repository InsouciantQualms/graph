/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.iq.graph.model.ComponentSpace;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Type;
import dev.iq.graph.model.jgrapht.JGraphtGraphBuilder;
import dev.iq.graph.model.simple.SimpleType;
import java.time.Instant;
import java.util.HashSet;
import org.jgrapht.graph.builder.GraphBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for GraphSpace interface and builder.
 */
@DisplayName("GraphSpace Interface Tests")
public class MutableGraphOperationsTest {

    private final Type defaultType = new SimpleType("test");

    @Test
    @DisplayName("GraphSpace builder creates graph successfully")
    final void testGraphBuilderCreation() {
        // Test that we can create a graph using the builder
        final GraphBuilder builder = new JGraphtGraphBuilder();
        assertNotNull(builder, "GraphSpace builder should not be null");

        // Add at least one node to satisfy validation requirement
        builder.nodes().add(defaultType, new TestData("Node1"), new HashSet<>(), Instant.now());

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
    @DisplayName("GraphSpace provides access to all operation interfaces")
    final void testGraphOperationInterfaces() {
        final GraphBuilder builder = new JGraphtGraphBuilder();

        // Add a node
        builder.nodes().add(defaultType, new TestData("Node1"), new HashSet<>(), Instant.now());

        final var graph = builder.validate().build();

        // Test that all operation interfaces are accessible
        assertInstanceOf(NodeOperations.class, graph.nodes());
        assertInstanceOf(EdgeOperations.class, graph.edges());
        assertInstanceOf(ComponentSpace.class, graph.components());
        assertInstanceOf(PathOperations.class, graph.paths());
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
