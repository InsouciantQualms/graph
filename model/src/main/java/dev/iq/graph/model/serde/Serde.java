/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.serde;

import dev.iq.graph.model.Data;

/**
 * Interface to allow for Data (containing a Java POJO) to be serialized and deserialized (serde) to and from
 * a given format (specified by type parameter S).
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
