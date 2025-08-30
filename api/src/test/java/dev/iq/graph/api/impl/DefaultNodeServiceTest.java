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
import dev.iq.graph.model.Node;
import dev.iq.graph.model.operations.NodeOperations;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultNodeService Unit Tests")
class DefaultNodeServiceTest {

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
    private NodeOperations nodeOperations;

    private DefaultNodeService nodeService;

    @BeforeEach
    void setUp() {
        when(graphRepository.nodes()).thenReturn(nodeRepository);
        when(graphRepository.edges()).thenReturn(edgeRepository);
        when(sessionFactory.create()).thenReturn(session);
        nodeService = new DefaultNodeService(graphRepository, sessionFactory, nodeOperations);
    }

    @Test
    @DisplayName("add creates and returns new node")
    void add_CreatesNewNode_Success() {
        final Data data = new SimpleData(Map.of("key", "value"));
        final Node expectedNode = createNode(Locator.generate().id());

        when(nodeOperations.add(any(NanoId.class), any(), eq(data), any(Instant.class)))
                .thenReturn(expectedNode);

        final Node result = nodeService.add(data);

        assertNotNull(result);
        assertEquals(expectedNode, result);
        verify(nodeOperations).add(any(NanoId.class), any(), eq(data), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("add handles exception and rolls back")
    void add_HandlesException_ThrowsRuntimeException() {
        final Data data = new SimpleData(Map.of("key", "value"));

        when(nodeOperations.add(any(NanoId.class), any(), eq(data), any(Instant.class)))
                .thenThrow(new RuntimeException("Test exception"));

        assertThrows(RuntimeException.class, () -> nodeService.add(data));
        verify(session).close();
    }

    @Test
    @DisplayName("update modifies existing node successfully")
    void update_ModifiesExistingNode_Success() {
        final NanoId nodeId = Locator.generate().id();
        final Data newData = new SimpleData(Map.of("updated", "data"));
        final Node existingNode = createNode(nodeId);
        final Node updatedNode = createNode(nodeId);

        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.of(existingNode));
        when(nodeOperations.update(eq(nodeId), any(), eq(newData), any(Instant.class)))
                .thenReturn(updatedNode);

        final Node result = nodeService.update(nodeId, newData);

        assertNotNull(result);
        assertEquals(updatedNode, result);
        verify(nodeRepository).findActive(nodeId);
        verify(nodeOperations).update(eq(nodeId), any(), eq(newData), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("update throws exception when node not found")
    void update_NodeNotFound_ThrowsException() {
        final NanoId nodeId = Locator.generate().id();
        final Data newData = new SimpleData(Map.of("updated", "data"));

        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> nodeService.update(nodeId, newData));
        verify(nodeRepository).findActive(nodeId);
        verify(nodeOperations, never()).update(any(), any(), any(), any());
        verify(session).close();
    }

    @Test
    @DisplayName("getNeighbors returns list of neighboring nodes")
    void getNeighbors_ReturnsNeighborNodes() {
        final NanoId nodeId = Locator.generate().id();
        final NanoId neighborId = Locator.generate().id();
        final Node node = createNode(nodeId);
        final Node neighbor = createNode(neighborId);

        when(nodeRepository.allActiveIds()).thenReturn(List.of(nodeId, neighborId));
        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.findActive(neighborId)).thenReturn(Optional.of(neighbor));
        when(edgeRepository.allActiveIds()).thenReturn(List.of(Locator.generate().id()));
        when(edgeRepository.findActive(any())).thenReturn(Optional.of(createEdge(nodeId, neighborId)));

        final List<Node> result = nodeService.getNeighbors(nodeId);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getNeighbors returns empty list when node not found")
    void getNeighbors_NodeNotFound_ReturnsEmptyList() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.allActiveIds()).thenReturn(Collections.emptyList());
        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.empty());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());

        final List<Node> result = nodeService.getNeighbors(nodeId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("find returns node by locator")
    void find_ReturnsNodeByLocator() {
        final Locator locator = Locator.generate();
        final Node expectedNode = createNode(locator.id());

        when(nodeRepository.find(locator)).thenReturn(expectedNode);

        final Node result = nodeService.find(locator);

        assertNotNull(result);
        assertEquals(expectedNode, result);
        verify(nodeRepository).find(locator);
    }

    @Test
    @DisplayName("find throws exception when node not found")
    void find_NodeNotFound_ThrowsException() {
        final Locator locator = Locator.generate();

        when(nodeRepository.find(locator)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> nodeService.find(locator));
        verify(nodeRepository).find(locator);
    }

    @Test
    @DisplayName("findActive returns Optional containing active node")
    void findActive_ReturnsOptionalWithNode() {
        final NanoId nodeId = Locator.generate().id();
        final Node expectedNode = createNode(nodeId);

        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.of(expectedNode));

        final Optional<Node> result = nodeService.findActive(nodeId);

        assertTrue(result.isPresent());
        assertEquals(expectedNode, result.get());
        verify(nodeRepository).findActive(nodeId);
    }

    @Test
    @DisplayName("findActive returns empty Optional when node not found")
    void findActive_NodeNotFound_ReturnsEmptyOptional() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.empty());

        final Optional<Node> result = nodeService.findActive(nodeId);

        assertFalse(result.isPresent());
        verify(nodeRepository).findActive(nodeId);
    }

    @Test
    @DisplayName("findAt returns node at specific timestamp")
    void findAt_ReturnsNodeAtTimestamp() {
        final NanoId nodeId = Locator.generate().id();
        final Instant timestamp = Instant.now();
        final Node expectedNode = createNode(nodeId);

        when(nodeRepository.findAt(nodeId, timestamp)).thenReturn(Optional.of(expectedNode));

        final Optional<Node> result = nodeService.findAt(nodeId, timestamp);

        assertTrue(result.isPresent());
        assertEquals(expectedNode, result.get());
        verify(nodeRepository).findAt(nodeId, timestamp);
    }

    @Test
    @DisplayName("findVersions returns all versions of node")
    void findVersions_ReturnsAllVersions() {
        final NanoId nodeId = Locator.generate().id();
        final List<Node> expectedVersions = List.of(
                createNode(nodeId),
                createNode(nodeId)
        );

        when(nodeRepository.findVersions(nodeId)).thenReturn(expectedVersions);

        final List<Node> result = nodeService.findVersions(nodeId);

        assertNotNull(result);
        assertEquals(expectedVersions.size(), result.size());
        verify(nodeRepository).findVersions(nodeId);
    }

    @Test
    @DisplayName("allActive returns list of active node IDs")
    void allActive_ReturnsActiveNodeIds() {
        final List<NanoId> expectedIds = List.of(
                Locator.generate().id(),
                Locator.generate().id()
        );

        when(nodeRepository.allActiveIds()).thenReturn(expectedIds);

        final List<NanoId> result = nodeService.allActive();

        assertNotNull(result);
        assertEquals(expectedIds, result);
        verify(nodeRepository).allActiveIds();
    }

    @Test
    @DisplayName("all returns list of all node IDs")
    void all_ReturnsAllNodeIds() {
        final List<NanoId> expectedIds = List.of(
                Locator.generate().id(),
                Locator.generate().id(),
                Locator.generate().id()
        );

        when(nodeRepository.allIds()).thenReturn(expectedIds);

        final List<NanoId> result = nodeService.all();

        assertNotNull(result);
        assertEquals(expectedIds, result);
        verify(nodeRepository).allIds();
    }

    @Test
    @DisplayName("expire marks node as expired")
    void expire_MarksNodeAsExpired_Success() {
        final NanoId nodeId = Locator.generate().id();
        final Node existingNode = createNode(nodeId);
        final Node expiredNode = createNode(nodeId);

        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.of(existingNode));
        when(nodeOperations.expire(eq(nodeId), any(Instant.class))).thenReturn(expiredNode);

        final Optional<Node> result = nodeService.expire(nodeId);

        assertTrue(result.isPresent());
        assertEquals(expiredNode, result.get());
        verify(nodeRepository).findActive(nodeId);
        verify(nodeOperations).expire(eq(nodeId), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("expire returns empty Optional when node not found")
    void expire_NodeNotFound_ReturnsEmptyOptional() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.findActive(nodeId)).thenReturn(Optional.empty());

        final Optional<Node> result = nodeService.expire(nodeId);

        assertFalse(result.isPresent());
        verify(nodeRepository).findActive(nodeId);
        verify(nodeOperations, never()).expire(any(), any());
        verify(session, never()).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("delete removes node permanently")
    void delete_RemovesNode_Success() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.delete(nodeId)).thenReturn(true);

        final boolean result = nodeService.delete(nodeId);

        assertTrue(result);
        verify(nodeRepository).delete(nodeId);
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("delete returns false when node not found")
    void delete_NodeNotFound_ReturnsFalse() {
        final NanoId nodeId = Locator.generate().id();

        when(nodeRepository.delete(nodeId)).thenReturn(false);

        final boolean result = nodeService.delete(nodeId);

        assertFalse(result);
        verify(nodeRepository).delete(nodeId);
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

    private dev.iq.graph.model.Edge createEdge(final NanoId sourceId, final NanoId targetId) {
        return new dev.iq.graph.model.Edge() {
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
            public java.util.Set<NanoId> components() {
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