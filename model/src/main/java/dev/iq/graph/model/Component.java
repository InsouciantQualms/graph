package dev.iq.graph.model;

import dev.iq.common.version.Versioned;

import java.util.List;

/**
 * Represents a versioned, maximally connected subgraph containing nodes and edges.
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
