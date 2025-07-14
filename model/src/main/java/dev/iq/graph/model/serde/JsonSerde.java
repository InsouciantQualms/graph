package dev.iq.graph.model.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iq.common.fp.Io;
import dev.iq.common.fp.Try;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.simple.SimpleData;

/**
 * Serde implementation that converts Data to and from JSON format.
 */
public final class JsonSerde implements Serde<String> {

    private static final String TYPE_FIELD = "_type";
    private static final String VALUE_FIELD = "_value";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes Data to JSON format including type information.
     */
    @Override
    public String serialize(final Data target) {
        return Try.withReturn(() -> {
            final var wrapper = objectMapper.createObjectNode();
            wrapper.put(TYPE_FIELD, target.type().getName());
            wrapper.set(VALUE_FIELD, objectMapper.valueToTree(target.value()));
            return objectMapper.writeValueAsString(wrapper);
        });
    }

    /**
     * Deserializes JSON to Data, restoring type information.
     */
    @Override
    public Data deserialize(final String target) {
        return Try.withReturn(() -> {
            final var targetNode = objectMapper.readTree(target);
            final var typeName = targetNode.get(TYPE_FIELD).asText();
            final var valueNode = targetNode.get(VALUE_FIELD);
            final var type = Io.withReturn(() -> Class.forName(typeName));
            final var value = objectMapper.treeToValue(valueNode, type);
            return new SimpleData(type, value);
        });
    }
}