/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


package dev.iq.graph.model.simple;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Element;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimpleComponent.
 */
class SimpleComponentTest {

    @Test
    void testSimpleComponentCreation() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final var expired = Optional.<Instant>empty();
        final List<Element> elements = List.of();

        final var component = new SimpleComponent(locator, elements, data, created, expired);

        assertEquals(locator, component.locator());
        assertEquals(elements, component.elements());
        assertEquals(data, component.data());
        assertEquals(created, component.created());
        assertEquals(expired, component.expired());
    }

    @Test
    void testSimpleComponentWithExpired() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final var expiredTime = created.plusSeconds(3600);
        final var expired = Optional.of(expiredTime);
        final List<Element> elements = List.of();

        final var component = new SimpleComponent(locator, elements, data, created, expired);

        assertTrue(component.expired().isPresent());
        assertEquals(expiredTime, component.expired().get());
    }

    @Test
    void testSimpleComponentWithElements() {
        final var nodeId = new NanoId("node-id");
        final var nodeLocator = new Locator(nodeId, 1);
        final var nodeData = new SimpleData(String.class, "Node Data");
        final var node = new SimpleNode(nodeLocator, List.of(), nodeData, Instant.now(), Optional.empty());

        final var componentId = new NanoId("component-id");
        final var componentLocator = new Locator(componentId, 1);
        final var componentData = new SimpleData(String.class, "Component Data");
        final List<Element> elements = List.of(node);

        final var component = new SimpleComponent(componentLocator, elements, componentData, Instant.now(), Optional.empty());

        assertEquals(1, component.elements().size());
        assertEquals(node, component.elements().get(0));
    }

    @Test
    void testSimpleComponentEquality() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final List<Element> elements = List.of();

        final var component1 = new SimpleComponent(locator, elements, data, created, Optional.empty());
        final var component2 = new SimpleComponent(locator, elements, data, created, Optional.empty());

        assertEquals(component1, component2);
        assertEquals(component1.hashCode(), component2.hashCode());
    }

    @Test
    void testSimpleComponentInequality() {
        final var id1 = new NanoId("component-id-1");
        final var id2 = new NanoId("component-id-2");
        final var locator1 = new Locator(id1, 1);
        final var locator2 = new Locator(id2, 1);
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final List<Element> elements = List.of();

        final var component1 = new SimpleComponent(locator1, elements, data, created, Optional.empty());
        final var component2 = new SimpleComponent(locator2, elements, data, created, Optional.empty());

        assertNotEquals(component1, component2);
    }

    @Test
    void testSimpleComponentToString() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final List<Element> elements = List.of();

        final var component = new SimpleComponent(locator, elements, data, created, Optional.empty());
        final var toString = component.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("SimpleComponent"));
        assertTrue(toString.contains(locator.toString()));
    }
}