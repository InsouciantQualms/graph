/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.Versioned;
import java.util.List;

/**
 * Represents a versioned, maximally connected subgraph containing nodes and edges.
 * Implementations must be immutable and thread-safe.
 */
public interface Component extends Versioned {

    /**
     * Returns the list of elements contained in this component.
     */
    List<Element> elements();

    /**
     * Returns the data associated with this component.
     */
    Data data();
}
