package dev.iq.graph.model.serde;

import dev.iq.graph.model.simple.SimpleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataSerializer utility class.
 */
@DisplayName("DataSerializer Tests")
final class PropertiesSerdeTest {

    private final Serde<Map<String, Object>> serde = new PropertiesSerde();

    @Test
    void testSimple() throws Exception {

        final var data = new SimpleData(Integer.class, 5);
        final var map = serde.serialize(data);
        final var value = serde.deserialize(map);
        assertEquals(data, value);
    }

    @Test
    @DisplayName("String data serialization and deserialization")
    void testStringDataSerialization() {
        final var originalData = new SimpleData(String.class, "test string");

        // Test properties serialization
        final var properties = serde.serialize(originalData);
        assertTrue(properties.containsKey("data._type"));
        assertEquals(String.class.getName(), properties.get("data._type"));

        // Test properties deserialization
        final var deserializedData = serde.deserialize(properties);
        assertSame(String.class, deserializedData.type());
        assertEquals("test string", deserializedData.value());
    }

    @Test
    @DisplayName("Integer data serialization and deserialization")
    void testIntegerDataSerialization() {
        final var originalData = new SimpleData(Integer.class, 42);

        // Test properties serialization
        final var properties = serde.serialize(originalData);
        assertTrue(properties.containsKey("data._type"));
        assertEquals(Integer.class.getName(), properties.get("data._type"));

        // Test properties deserialization
        final var deserializedData = serde.deserialize(properties);
        assertSame(Integer.class, deserializedData.type());
        assertEquals(42, deserializedData.value());
    }

    @Test
    @DisplayName("POJO data serialization and deserialization")
    void testPojoDataSerialization() {
        final var complexData = new TestPojo("test name", 123, true);
        final var originalData = new SimpleData(TestPojo.class, complexData);

        // Test properties serialization
        final var properties = serde.serialize(originalData);
        assertTrue(properties.containsKey("data._type"));
        assertEquals(TestPojo.class.getName(), properties.get("data._type"));

        // Test properties deserialization
        final var deserializedData = serde.deserialize(properties);
        assertSame(TestPojo.class, deserializedData.type());

        final var deserializedObject = (TestPojo) deserializedData.value();
        assertEquals("test name", deserializedObject.name());
        assertEquals(123, deserializedObject.value());
        assertTrue(deserializedObject.flag());
    }


    @Test
    @DisplayName("Round trip properties serialization maintains data integrity")
    void testRoundTripPropertiesSerialization() {
        final var complexData = new TestPojo("roundtrip", 999, false);
        final var originalData = new SimpleData(TestPojo.class, complexData);

        // Serialize to properties
        final var properties = serde.serialize(originalData);

        // Deserialize from properties
        final var deserializedData = serde.deserialize(properties);

        // Verify data integrity
        assertSame(originalData.type(), deserializedData.type());
        final var deserializedObject = (TestPojo) deserializedData.value();
        assertEquals("roundtrip", deserializedObject.name());
        assertEquals(999, deserializedObject.value());
        assertFalse(deserializedObject.flag());
    }
    
    /**
     * Test POJO class for serialization testing.
     */
    private record TestPojo(String name, int value, boolean flag) {}
}