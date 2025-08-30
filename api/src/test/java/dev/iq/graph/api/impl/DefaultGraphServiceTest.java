/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Path;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.EdgeRepository;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.NodeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultGraphService Unit Tests")
class DefaultGraphServiceTest {

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private EdgeRepository edgeRepository;

    private DefaultGraphService graphService;

    @BeforeEach
    void setUp() {
        when(graphRepository.nodes()).thenReturn(nodeRepository);
        when(graphRepository.edges()).thenReturn(edgeRepository);
        graphService = new DefaultGraphService(graphRepository);
    }

    @Test
    @DisplayName("hasPath returns true when path exists between nodes")
    void hasPath_WhenPathExists_ReturnsTrue() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);
        final Node targetNode = createNode(targetId);
        final Edge edge = createEdge(sourceId, targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId, targetId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(List.of(edge.locator().id()));
        when(edgeRepository.findActive(any())).thenReturn(Optional.of(edge));

        final boolean result = graphService.hasPath(sourceId, targetId);

        assertTrue(result);
        verify(nodeRepository, atLeastOnce()).findActive(sourceId);
        verify(nodeRepository, atLeastOnce()).findActive(targetId);
    }

    @Test
    @DisplayName("hasPath returns false when source node not found")
    void hasPath_WhenSourceNodeNotFound_ReturnsFalse() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node targetNode = createNode(targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(targetId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.empty());
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final boolean result = graphService.hasPath(sourceId, targetId);

        assertFalse(result);
    }

    @Test
    @DisplayName("hasPath returns false when target node not found")
    void hasPath_WhenTargetNodeNotFound_ReturnsFalse() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.empty());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final boolean result = graphService.hasPath(sourceId, targetId);

        assertFalse(result);
    }

    @Test
    @DisplayName("hasPath returns false when no path exists between nodes")
    void hasPath_WhenNoPathExists_ReturnsFalse() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);
        final Node targetNode = createNode(targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId, targetId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final boolean result = graphService.hasPath(sourceId, targetId);

        assertFalse(result);
    }

    @Test
    @DisplayName("getActiveConnected returns all connected paths")
    void getActiveConnected_ReturnsConnectedPaths() {
        final NanoId node1Id = Locator.generate().id();
        final NanoId node2Id = Locator.generate().id();
        final NanoId node3Id = Locator.generate().id();
        final Node node1 = createNode(node1Id);
        final Node node2 = createNode(node2Id);
        final Node node3 = createNode(node3Id);
        final Edge edge1 = createEdge(node1Id, node2Id);
        final Edge edge2 = createEdge(node2Id, node3Id);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(node1Id, node2Id, node3Id));
        when(nodeRepository.findActive(node1Id)).thenReturn(Optional.of(node1));
        when(nodeRepository.findActive(node2Id)).thenReturn(Optional.of(node2));
        when(nodeRepository.findActive(node3Id)).thenReturn(Optional.of(node3));
        when(edgeRepository.allActiveIds()).thenReturn(List.of(edge1.locator().id(), edge2.locator().id()));
        when(edgeRepository.findActive(edge1.locator().id())).thenReturn(Optional.of(edge1));
        when(edgeRepository.findActive(edge2.locator().id())).thenReturn(Optional.of(edge2));

        final List<Path> result = graphService.getActiveConnected();

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getActiveConnected returns empty list when no nodes exist")
    void getActiveConnected_WhenNoNodes_ReturnsEmptyList() {
        when(nodeRepository.allActiveIds()).thenReturn(Collections.emptyList());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final List<Path> result = graphService.getActiveConnected();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getShortestPath returns path when exists")
    void getShortestPath_WhenPathExists_ReturnsPath() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);
        final Node targetNode = createNode(targetId);
        final Edge edge = createEdge(sourceId, targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId, targetId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(List.of(edge.locator().id()));
        when(edgeRepository.findActive(any())).thenReturn(Optional.of(edge));

        final Path result = graphService.getShortestPath(sourceId, targetId);

        assertNotNull(result);
    }

    @Test
    @DisplayName("getShortestPath throws when source node not found")
    void getShortestPath_WhenSourceNodeNotFound_ThrowsException() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node targetNode = createNode(targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(targetId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.empty());
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () ->
                graphService.getShortestPath(sourceId, targetId));
    }

    @Test
    @DisplayName("getShortestPath throws when target node not found")
    void getShortestPath_WhenTargetNodeNotFound_ThrowsException() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.empty());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () ->
                graphService.getShortestPath(sourceId, targetId));
    }

    @Test
    @DisplayName("getShortestPath throws when no path exists")
    void getShortestPath_WhenNoPathExists_ThrowsException() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);
        final Node targetNode = createNode(targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId, targetId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        assertThrows(IllegalStateException.class, () ->
                graphService.getShortestPath(sourceId, targetId));
    }

    private Node createNode(final NanoId id) {
        return new Node() {
            @Override
            public Locator locator() {
                return new Locator(id, 1);
            }

            @Override
            public SimpleType type() {
                return new SimpleType("NODE");
            }

            @Override
            public Data data() {
                return new SimpleData(Collections.emptyMap());
            }

            @Override
            public Instant created() {
                return Instant.now();
            }

            @Override
            public Optional<Instant> expired() {
                return Optional.empty();
            }

            @Override
            public boolean isActive() {
                return true;
            }
        };
    }

    private Edge createEdge(final NanoId sourceId, final NanoId targetId) {
        return new Edge() {
            @Override
            public Locator locator() {
                return Locator.generate();
            }

            @Override
            public SimpleType type() {
                return new SimpleType("EDGE");
            }

            @Override
            public NanoId source() {
                return sourceId;
            }

            @Override
            public NanoId target() {
                return targetId;
            }

            @Override
            public Data data() {
                return new SimpleData(Collections.emptyMap());
            }

            @Override
            public Set<NanoId> components() {
                return Collections.emptySet();
            }

            @Override
            public Instant created() {
                return Instant.now();
            }

            @Override
            public Optional<Instant> expired() {
                return Optional.empty();
            }

            @Override
            public boolean isActive() {
                return true;
            }
        };
    }
}