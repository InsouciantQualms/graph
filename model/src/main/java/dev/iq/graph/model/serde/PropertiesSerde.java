/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.serde;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.unflattener.JsonUnflattener;

import dev.iq.common.fp.Io;
import dev.iq.common.fp.Try;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.simple.SimpleData;

/**
 * Serde implementation that converts Data to and from flattened properties using the existing DataSerializer.
 */
public final class PropertiesSerde implements Serde<Map<String, Object>> {

    private static final String DATA_TYPE = "data._type";

    private static final String SCALAR_FLAG = "root";

    private static final String DATA_PREFIX = "data.";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes Data to flattened properties.  Each value's key will be prefixed by DATA_PREFIX.
     */
    @Override
    public Map<String, Object> serialize(final Data data) {

        return Try.withReturn(() -> {
            final var properties = new HashMap<String, Object>();
            properties.put(DATA_TYPE, data.type().getName());
            final var jsonString = objectMapper.writeValueAsString(data.value());
            final var flattenedMap = JsonFlattener.flattenAsMap(jsonString);
            flattenedMap.forEach((key, value) -> properties.put(DATA_PREFIX + key, value));
            return properties;
        });
    }

    /**
     * Deserializes flattened properties to Data.
     */
    @Override
    public Data deserialize(final Map<String, Object> properties) {

        final var type = resolveType(properties);
        final var dataProperties = restoreKeys(properties);

        // Handle scalar (simple) types that json-flattener wraps in a "root" object
        if ((dataProperties.size() == 1) && dataProperties.containsKey(SCALAR_FLAG)) {
            return Try.withReturn(() -> {
                final var rootValue = dataProperties.get(SCALAR_FLAG);
                final var value = objectMapper.convertValue(rootValue, type);
                return new SimpleData(type, value);
            });
        }

        // Unflatten complex types back to JSON and deserialize normally
        final var unflattenedJson = JsonUnflattener.unflatten(dataProperties);
        return Try.withReturn(() -> {
            final var jsonNode = objectMapper.readTree(unflattenedJson);
            final var value = objectMapper.treeToValue(jsonNode, type);
            return new SimpleData(type, value);
        });
    }

    /**
     * Removes the DATA_PREFIX from keys that were stored in the properties.
     */
    private static Map<String, Object> restoreKeys(final Map<String, Object> properties) {

        // Extract data properties (those starting with "data.")
        final var dataProperties = new HashMap<String, Object>();
        properties.forEach((key, value) -> {
            if (key.startsWith(DATA_PREFIX) && !key.equals(DATA_TYPE)) {
                dataProperties.put(key.substring(DATA_PREFIX.length()), value);
            }
        });
        return dataProperties;
    }

    /**
     * Determine the type to deserialize.
     */
    private static Class<?> resolveType(final Map<String, Object> properties) {

        final var typeName = (String) properties.get(DATA_TYPE);
        if (typeName == null) {
            throw new IllegalArgumentException("Missing type information in properties");
        }
        return Io.withReturn(() -> Class.forName(typeName));
    }
}
