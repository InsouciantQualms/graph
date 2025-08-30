/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.api.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.operations.EdgeOperations;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.EdgeRepository;
import dev.iq.graph.persistence.GraphRepository;
import dev.iq.graph.persistence.NodeRepository;
import dev.iq.graph.persistence.Session;
import dev.iq.graph.persistence.SessionFactory;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultEdgeService Unit Tests")
class DefaultEdgeServiceTest {

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private Session session;

    @Mock
    private EdgeOperations edgeOperations;

    private DefaultEdgeService edgeService;

    @BeforeEach
    void setUp() {
        when(graphRepository.nodes()).thenReturn(nodeRepository);
        when(graphRepository.edges()).thenReturn(edgeRepository);
        when(sessionFactory.create()).thenReturn(session);
        edgeService = new DefaultEdgeService(graphRepository, sessionFactory, edgeOperations);
    }

    @Test
    @DisplayName("addEdge creates new edge between nodes")
    void addEdge_CreatesNewEdge_Success() {
        final Node sourceNode = createNode(Locator.generate().id());
        final Node targetNode = createNode(Locator.generate().id());
        final Data data = new SimpleData(Map.of("weight", 1.0));
        final Edge expectedEdge = createEdge(sourceNode.locator().id(), targetNode.locator().id());

        when(edgeOperations.add(any(), eq(sourceNode), eq(targetNode), eq(data), any(Set.class), any(Instant.class)))
                .thenReturn(expectedEdge);

        final Edge result = edgeService.addEdge(sourceNode, targetNode, data);

        assertNotNull(result);
        assertEquals(expectedEdge, result);
        verify(edgeOperations).add(any(), eq(sourceNode), eq(targetNode), eq(data), any(Set.class), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("addEdge handles exception and throws RuntimeException")
    void addEdge_HandlesException_ThrowsRuntimeException() {
        final Node sourceNode = createNode(Locator.generate().id());
        final Node targetNode = createNode(Locator.generate().id());
        final Data data = new SimpleData(Map.of("weight", 1.0));

        when(edgeOperations.add(any(), any(), any(), any(), any(Set.class), any(Instant.class)))
                .thenThrow(new RuntimeException("Test exception"));

        assertThrows(RuntimeException.class, () -> edgeService.addEdge(sourceNode, targetNode, data));
        verify(session).close();
    }

    @Test
    @DisplayName("updateEdge modifies existing edge successfully")
    void updateEdge_ModifiesExistingEdge_Success() {
        final NanoId edgeId = Locator.generate().id();
        final Data newData = new SimpleData(Map.of("weight", 2.0));
        final Edge existingEdge = createEdge(Locator.generate().id(), Locator.generate().id());
        final Edge updatedEdge = createEdge(existingEdge.source(), existingEdge.target());

        when(edgeRepository.findActive(edgeId)).thenReturn(Optional.of(existingEdge));
        when(edgeOperations.update(eq(edgeId), any(), eq(newData), any(Set.class), any(Instant.class)))
                .thenReturn(updatedEdge);

        final Edge result = edgeService.updateEdge(edgeId, newData);

        assertNotNull(result);
        assertEquals(updatedEdge, result);
        verify(edgeRepository).findActive(edgeId);
        verify(edgeOperations).update(eq(edgeId), any(), eq(newData), any(Set.class), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("updateEdge throws exception when edge not found")
    void updateEdge_EdgeNotFound_ThrowsException() {
        final NanoId edgeId = Locator.generate().id();
        final Data newData = new SimpleData(Map.of("weight", 2.0));

        when(edgeRepository.findActive(edgeId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> edgeService.updateEdge(edgeId, newData));
        verify(edgeRepository).findActive(edgeId);
        verify(edgeOperations, never()).update(any(), any(), any(), any(Set.class), any());
        verify(session).close();
    }

    @Test
    @DisplayName("getEdgesFrom returns outgoing edges from node")
    void getEdgesFrom_ReturnsOutgoingEdges() {
        final NanoId nodeId = Locator.generate().id();
        final NanoId targetId = Locator.generate().id();
        final Node node = createNode(nodeId);
        final Node targetNode = createNode(targetId);
        final Edge edge = createEdge(nodeId, targetId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(nodeId, targetId));
        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findActive(targetId)).thenReturn(Optional.of(targetNode));
        when(edgeRepository.allActiveIds()).thenReturn(List.of(edge.locator().id()));
        when(edgeRepository.findActive(edge.locator().id())).thenReturn(Optional.of(edge));

        final List<Edge> result = edgeService.getEdgesFrom(nodeId);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getEdgesFrom returns empty list when node not found")
    void getEdgesFrom_NodeNotFound_ReturnsEmptyList() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.allActiveIds()).thenReturn(Collections.emptyList());
        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.empty());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final List<Edge> result = edgeService.getEdgesFrom(nodeId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getEdgesTo returns incoming edges to node")
    void getEdgesTo_ReturnsIncomingEdges() {
        final NanoId sourceId = Locator.generate().id();
        final NanoId nodeId = Locator.generate().id();
        final Node sourceNode = createNode(sourceId);
        final Node node = createNode(nodeId);
        final Edge edge = createEdge(sourceId, nodeId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(sourceId, nodeId));
        when(nodeRepository.findActive(sourceId)).thenReturn(Optional.of(sourceNode));
        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.of(node));
        when(edgeRepository.allActiveIds()).thenReturn(List.of(edge.locator().id()));
        when(edgeRepository.findActive(edge.locator().id())).thenReturn(Optional.of(edge));

        final List<Edge> result = edgeService.getEdgesTo(nodeId);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getEdgesTo returns empty list when node not found")
    void getEdgesTo_NodeNotFound_ReturnsEmptyList() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.allActiveIds()).thenReturn(Collections.emptyList());
        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.empty());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final List<Edge> result = edgeService.getEdgesTo(nodeId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("find returns edge by locator")
    void find_ReturnsEdgeByLocator() {
        final Locator locator = Locator.generate();
        final Edge expectedEdge = createEdge(Locator.generate().id(), Locator.generate().id());

        when(edgeRepository.find(locator)).thenReturn(expectedEdge);

        final Edge result = edgeService.find(locator);

        assertNotNull(result);
        assertEquals(expectedEdge, result);
        verify(edgeRepository).find(locator);
    }

    @Test
    @DisplayName("find throws exception when edge not found")
    void find_EdgeNotFound_ThrowsException() {
        final Locator locator = Locator.generate();

        when(edgeRepository.find(locator)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> edgeService.find(locator));
        verify(edgeRepository).find(locator);
    }

    @Test
    @DisplayName("findActive returns Optional containing active edge")
    void findActive_ReturnsOptionalWithEdge() {
        final NanoId edgeId = Locator.generate().id();
        final Edge expectedEdge = createEdge(Locator.generate().id(), Locator.generate().id());

        when(edgeRepository.findActive(edgeId)).thenReturn(Optional.of(expectedEdge));

        final Optional<Edge> result = edgeService.findActive(edgeId);

        assertTrue(result.isPresent());
        assertEquals(expectedEdge, result.get());
        verify(edgeRepository).findActive(edgeId);
    }

    @Test
    @DisplayName("findAt returns edge at specific timestamp")
    void findAt_ReturnsEdgeAtTimestamp() {
        final NanoId edgeId = Locator.generate().id();
        final Instant timestamp = Instant.now();
        final Edge expectedEdge = createEdge(Locator.generate().id(), Locator.generate().id());

        when(edgeRepository.findAt(edgeId, timestamp)).thenReturn(Optional.of(expectedEdge));

        final Optional<Edge> result = edgeService.findAt(edgeId, timestamp);

        assertTrue(result.isPresent());
        assertEquals(expectedEdge, result.get());
        verify(edgeRepository).findAt(edgeId, timestamp);
    }

    @Test
    @DisplayName("findVersions returns all versions of edge")
    void findVersions_ReturnsAllVersions() {
        final NanoId edgeId = Locator.generate().id();
        final List<Edge> expectedVersions = List.of(
                createEdge(Locator.generate().id(), Locator.generate().id()),
                createEdge(Locator.generate().id(), Locator.generate().id())
        );

        when(edgeRepository.findVersions(edgeId)).thenReturn(expectedVersions);

        final List<Edge> result = edgeService.findVersions(edgeId);

        assertNotNull(result);
        assertEquals(expectedVersions.size(), result.size());
        verify(edgeRepository).findVersions(edgeId);
    }

    @Test
    @DisplayName("allActive returns list of active edge IDs")
    void allActive_ReturnsActiveEdgeIds() {
        final List<NanoId> expectedIds = List.of(
                Locator.generate().id(),
                Locator.generate().id()
        );

        when(edgeRepository.allActiveIds()).thenReturn(expectedIds);

        final List<NanoId> result = edgeService.allActive();

        assertNotNull(result);
        assertEquals(expectedIds, result);
        verify(edgeRepository).allActiveIds();
    }

    @Test
    @DisplayName("expire marks edge as expired")
    void expire_MarksEdgeAsExpired_Success() {
        final NanoId edgeId = Locator.generate().id();
        final Edge existingEdge = createEdge(Locator.generate().id(), Locator.generate().id());
        final Edge expiredEdge = createEdge(existingEdge.source(), existingEdge.target());

        when(edgeRepository.findActive(edgeId)).thenReturn(Optional.of(existingEdge));
        when(edgeOperations.expire(eq(edgeId), any(Instant.class))).thenReturn(expiredEdge);

        final Optional<Edge> result = edgeService.expire(edgeId);

        assertTrue(result.isPresent());
        assertEquals(expiredEdge, result.get());
        verify(edgeRepository).findActive(edgeId);
        verify(edgeOperations).expire(eq(edgeId), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("delete removes edge permanently")
    void delete_RemovesEdge_Success() {
        final NanoId edgeId = Locator.generate().id();

        when(edgeRepository.delete(edgeId)).thenReturn(true);

        final boolean result = edgeService.delete(edgeId);

        assertTrue(result);
        verify(edgeRepository).delete(edgeId);
        verify(session).commit();
        verify(session).close();
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