package dev.iq.graph.model.serde;

import dev.iq.graph.model.Data;

/**
 * Iinterface to allow for Data (containing a Java POJO) to be serialized and deserialized (serde) to and from
 * a given format (specified by type parameter `<S>`.`
 */
public interface Serde<S> {

    /**
     * Serializes an instance.
     */
    S serialize(Data target);

    /**
     * Deserializes an instance.
     */
    Data deserialize(S target);
}
