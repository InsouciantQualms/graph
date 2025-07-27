package dev.iq.graph.model;

import dev.iq.common.adt.Coded;

/**
 * Describes the type of the graph element.  The type of an element can always be resolved via its string code
 * value.
 */
public interface Type extends Coded<String> {}
