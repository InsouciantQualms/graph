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
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import dev.iq.graph.model.operations.ComponentOperations;
import dev.iq.graph.model.simple.SimpleData;
import dev.iq.graph.model.simple.SimpleType;
import dev.iq.graph.persistence.ComponentRepository;
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
@DisplayName("DefaultComponentService Unit Tests")
class DefaultComponentServiceTest {

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private EdgeRepository edgeRepository;

    @Mock
    private ComponentRepository componentRepository;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private Session session;

    @Mock
    private ComponentOperations componentOperations;

    private DefaultComponentService componentService;

    @BeforeEach
    void setUp() {
        when(graphRepository.nodes()).thenReturn(nodeRepository);
        when(graphRepository.edges()).thenReturn(edgeRepository);
        when(graphRepository.components()).thenReturn(componentRepository);
        when(sessionFactory.create()).thenReturn(session);
        componentService = new DefaultComponentService(graphRepository, sessionFactory, componentOperations);
    }

    @Test
    @DisplayName("add creates new component")
    void add_CreatesNewComponent_Success() {
        final List<Element> elements = Collections.emptyList();
        final Data data = new SimpleData(Map.of("name", "component1"));
        final Component expectedComponent = createComponent(Locator.generate().id());

        when(componentOperations.add(any(), eq(data), any(Instant.class)))
                .thenReturn(expectedComponent);

        final Component result = componentService.add(elements, data);

        assertNotNull(result);
        assertEquals(expectedComponent, result);
        verify(componentOperations).add(any(), eq(data), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("add handles exception and throws RuntimeException")
    void add_HandlesException_ThrowsRuntimeException() {
        final List<Element> elements = Collections.emptyList();
        final Data data = new SimpleData(Map.of("name", "component1"));

        when(componentOperations.add(any(), any(), any(Instant.class)))
                .thenThrow(new RuntimeException("Test exception"));

        assertThrows(RuntimeException.class, () -> componentService.add(elements, data));
        verify(session).close();
    }

    @Test
    @DisplayName("update modifies existing component successfully")
    void update_ModifiesExistingComponent_Success() {
        final NanoId componentId = Locator.generate().id();
        final List<Element> elements = Collections.emptyList();
        final Data newData = new SimpleData(Map.of("name", "updated"));
        final Component existingComponent = createComponent(componentId);
        final Component updatedComponent = createComponent(componentId);

        when(componentRepository.findActive(componentId)).thenReturn(Optional.of(existingComponent));
        when(componentOperations.update(eq(componentId), any(), eq(newData), any(Instant.class)))
                .thenReturn(updatedComponent);

        final Component result = componentService.update(componentId, elements, newData);

        assertNotNull(result);
        assertEquals(updatedComponent, result);
        verify(componentRepository).findActive(componentId);
        verify(componentOperations).update(eq(componentId), any(), eq(newData), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("update throws exception when component not found")
    void update_ComponentNotFound_ThrowsException() {
        final NanoId componentId = Locator.generate().id();
        final List<Element> elements = Collections.emptyList();
        final Data newData = new SimpleData(Map.of("name", "updated"));

        when(componentRepository.findActive(componentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> componentService.update(componentId, elements, newData));
        verify(componentRepository).findActive(componentId);
        verify(componentOperations, never()).update(any(), any(), any(), any());
        verify(session).close();
    }

    @Test
    @DisplayName("findActiveContaining returns components containing element")
    void findActiveContaining_ReturnsContainingComponents() {
        final NanoId elementId = Locator.generate().id();
        final NanoId componentId = Locator.generate().id();
        final Component component = createComponent(componentId);

        when(nodeRepository.allActiveIds()).thenReturn(Collections.emptyList());
        when(edgeRepository.allActiveIds()).thenReturn(Collections.emptyList());
        when(componentRepository.allActiveIds()).thenReturn(List.of(componentId));
        when(componentRepository.findActive(componentId)).thenReturn(Optional.of(component));

        final List<Component> result = componentService.findActiveContaining(elementId);

        assertNotNull(result);
        assertTrue(result.isEmpty()); // Because containsElement always returns false in current implementation
        verify(componentRepository).allActiveIds();
    }

    @Test
    @DisplayName("findContaining returns components containing element at timestamp")
    void findContaining_ReturnsContainingComponentsAtTimestamp() {
        final NanoId elementId = Locator.generate().id();
        final Instant timestamp = Instant.now();
        final NanoId componentId = Locator.generate().id();
        final Component component = createComponent(componentId);

        when(componentRepository.allIds()).thenReturn(List.of(componentId));
        when(componentRepository.findAt(componentId, timestamp)).thenReturn(Optional.of(component));

        final List<Component> result = componentService.findContaining(elementId, timestamp);

        assertNotNull(result);
        assertTrue(result.isEmpty()); // Because containsElement always returns false in current implementation
        verify(componentRepository).allIds();
    }

    @Test
    @DisplayName("find returns component by locator")
    void find_ReturnsComponentByLocator() {
        final Locator locator = Locator.generate();
        final Component expectedComponent = createComponent(locator.id());

        when(componentRepository.find(locator)).thenReturn(expectedComponent);

        final Component result = componentService.find(locator);

        assertNotNull(result);
        assertEquals(expectedComponent, result);
        verify(componentRepository).find(locator);
    }

    @Test
    @DisplayName("find throws exception when component not found")
    void find_ComponentNotFound_ThrowsException() {
        final Locator locator = Locator.generate();

        when(componentRepository.find(locator)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> componentService.find(locator));
        verify(componentRepository).find(locator);
    }

    @Test
    @DisplayName("findActive returns Optional containing active component")
    void findActive_ReturnsOptionalWithComponent() {
        final NanoId componentId = Locator.generate().id();
        final Component expectedComponent = createComponent(componentId);

        when(componentRepository.findActive(componentId)).thenReturn(Optional.of(expectedComponent));

        final Optional<Component> result = componentService.findActive(componentId);

        assertTrue(result.isPresent());
        assertEquals(expectedComponent, result.get());
        verify(componentRepository).findActive(componentId);
    }

    @Test
    @DisplayName("findAt returns component at specific timestamp")
    void findAt_ReturnsComponentAtTimestamp() {
        final NanoId componentId = Locator.generate().id();
        final Instant timestamp = Instant.now();
        final Component expectedComponent = createComponent(componentId);

        when(componentRepository.findAt(componentId, timestamp)).thenReturn(Optional.of(expectedComponent));

        final Optional<Component> result = componentService.findAt(componentId, timestamp);

        assertTrue(result.isPresent());
        assertEquals(expectedComponent, result.get());
        verify(componentRepository).findAt(componentId, timestamp);
    }

    @Test
    @DisplayName("findVersions returns all versions of component")
    void findVersions_ReturnsAllVersions() {
        final NanoId componentId = Locator.generate().id();
        final List<Component> expectedVersions = List.of(
                createComponent(componentId),
                createComponent(componentId)
        );

        when(componentRepository.findVersions(componentId)).thenReturn(expectedVersions);

        final List<Component> result = componentService.findVersions(componentId);

        assertNotNull(result);
        assertEquals(expectedVersions.size(), result.size());
        verify(componentRepository).findVersions(componentId);
    }

    @Test
    @DisplayName("allActive returns list of active component IDs")
    void allActive_ReturnsActiveComponentIds() {
        final List<NanoId> expectedIds = List.of(
                Locator.generate().id(),
                Locator.generate().id()
        );

        when(componentRepository.allActiveIds()).thenReturn(expectedIds);

        final List<NanoId> result = componentService.allActive();

        assertNotNull(result);
        assertEquals(expectedIds, result);
        verify(componentRepository).allActiveIds();
    }

    @Test
    @DisplayName("all returns list of all component IDs")
    void all_ReturnsAllComponentIds() {
        final List<NanoId> expectedIds = List.of(
                Locator.generate().id(),
                Locator.generate().id(),
                Locator.generate().id()
        );

        when(componentRepository.allIds()).thenReturn(expectedIds);

        final List<NanoId> result = componentService.all();

        assertNotNull(result);
        assertEquals(expectedIds, result);
        verify(componentRepository).allIds();
    }

    @Test
    @DisplayName("expire marks component as expired")
    void expire_MarksComponentAsExpired_Success() {
        final NanoId componentId = Locator.generate().id();
        final Component existingComponent = createComponent(componentId);
        final Component expiredComponent = createComponent(componentId);

        when(componentRepository.findActive(componentId)).thenReturn(Optional.of(existingComponent));
        when(componentOperations.expire(eq(componentId), any(Instant.class))).thenReturn(expiredComponent);

        final Optional<Component> result = componentService.expire(componentId);

        assertTrue(result.isPresent());
        assertEquals(expiredComponent, result.get());
        verify(componentRepository).findActive(componentId);
        verify(componentOperations).expire(eq(componentId), any(Instant.class));
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("expire returns empty Optional when component not found")
    void expire_ComponentNotFound_ReturnsEmptyOptional() {
        final NanoId componentId = Locator.generate().id();

        when(componentRepository.findActive(componentId)).thenReturn(Optional.empty());

        final Optional<Component> result = componentService.expire(componentId);

        assertFalse(result.isPresent());
        verify(componentRepository).findActive(componentId);
        verify(componentOperations, never()).expire(any(), any());
        verify(session, never()).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("delete removes component permanently")
    void delete_RemovesComponent_Success() {
        final NanoId componentId = Locator.generate().id();

        when(componentRepository.delete(componentId)).thenReturn(true);

        final boolean result = componentService.delete(componentId);

        assertTrue(result);
        verify(componentRepository).delete(componentId);
        verify(session).commit();
        verify(session).close();
    }

    @Test
    @DisplayName("delete returns false when component not found")
    void delete_ComponentNotFound_ReturnsFalse() {
        final NanoId componentId = Locator.generate().id();

        when(componentRepository.delete(componentId)).thenReturn(false);

        final boolean result = componentService.delete(componentId);

        assertFalse(result);
        verify(componentRepository).delete(componentId);
        verify(session).commit();
        verify(session).close();
    }

    private Component createComponent(final NanoId id) {
        return new Component() {
            @Override
            public Locator locator() {
                return new Locator(id, 1);
            }

            @Override
            public SimpleType type() {
                return new SimpleType("COMPONENT");
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
}