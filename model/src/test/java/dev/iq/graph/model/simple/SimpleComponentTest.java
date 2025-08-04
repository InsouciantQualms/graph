/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SimpleComponent.
 * Tests the new architecture where components are pure metadata objects with Type and Data.
 */
class SimpleComponentTest {

    @Test
    final void testSimpleComponentCreation() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var type = new SimpleType("component");
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final var expired = Optional.<Instant>empty();

        final var component = new SimpleComponent(locator, type, data, created, expired);

        assertEquals(locator, component.locator());
        assertEquals(type, component.type());
        assertEquals(data, component.data());
        assertEquals(created, component.created());
        assertEquals(expired, component.expired());
    }

    @Test
    final void testSimpleComponentWithExpired() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var type = new SimpleType("component");
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();
        final var expiredTime = created.plusSeconds(3600);
        final var expired = Optional.of(expiredTime);

        final var component = new SimpleComponent(locator, type, data, created, expired);

        assertTrue(component.expired().isPresent());
        assertEquals(expiredTime, component.expired().get());
    }

    @Test
    final void testSimpleComponentTypeVariations() {
        final var id = new NanoId("component-id");
        final var locator = new Locator(id, 1);
        final var type1 = new SimpleType("metadata");
        final var type2 = new SimpleType("annotation");
        final var data = new SimpleData(String.class, "Component Data");
        final var created = Instant.now();

        final var component1 = new SimpleComponent(locator, type1, data, created, Optional.empty());
        final var component2 = new SimpleComponent(locator, type2, data, created, Optional.empty());

        assertEquals(type1, component1.type());
        assertEquals(type2, component2.type());
        assertEquals("metadata", component1.type().code());
        assertEquals("annotation", component2.type().code());
    }

    @Test
    final void testSimpleComponentEquality() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var type = new SimpleType("component");
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();

        final var component1 = new SimpleComponent(locator, type, data, created, Optional.empty());
        final var component2 = new SimpleComponent(locator, type, data, created, Optional.empty());

        assertEquals(component1, component2);
        assertEquals(component1.hashCode(), component2.hashCode());
    }

    @Test
    final void testSimpleComponentInequality() {
        final var id1 = new NanoId("component-id-1");
        final var id2 = new NanoId("component-id-2");
        final var locator1 = new Locator(id1, 1);
        final var locator2 = new Locator(id2, 1);
        final var type = new SimpleType("component");
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();

        final var component1 = new SimpleComponent(locator1, type, data, created, Optional.empty());
        final var component2 = new SimpleComponent(locator2, type, data, created, Optional.empty());

        assertNotEquals(component1, component2);
    }

    @Test
    final void testSimpleComponentVersioning() {
        final var id = new NanoId("component-id");
        final var locator1 = new Locator(id, 1);
        final var locator2 = new Locator(id, 2);
        final var type = new SimpleType("component");
        final var data1 = new SimpleData(String.class, "Version 1");
        final var data2 = new SimpleData(String.class, "Version 2");
        final var created = Instant.now();

        final var component1 = new SimpleComponent(locator1, type, data1, created, Optional.empty());
        final var component2 = new SimpleComponent(locator2, type, data2, created.plusSeconds(1), Optional.empty());

        assertEquals(component1.locator().id(), component2.locator().id());
        assertNotEquals(component1.locator().version(), component2.locator().version());
        assertNotEquals(component1, component2);
    }

    @Test
    final void testSimpleComponentToString() {
        final var id = new NanoId("test-component-id");
        final var locator = new Locator(id, 1);
        final var type = new SimpleType("component");
        final var data = new SimpleData(String.class, "Test Component");
        final var created = Instant.now();

        final var component = new SimpleComponent(locator, type, data, created, Optional.empty());
        final var toString = component.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("SimpleComponent"));
        assertTrue(toString.contains(locator.toString()));
    }
}
