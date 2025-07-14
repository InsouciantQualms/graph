package dev.iq.graph.model;

import dev.iq.common.adt.Stable;

@Stable
public interface Edge extends Element {

    Node source();

    Node target();
}
