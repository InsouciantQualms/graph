/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */
package dev.iq.graph.persistence;

import org.jgrapht.event.GraphListener;

import dev.iq.common.adt.Stable;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;

/**
 * Repository that listens to graph events, queueing up persistence operations to
 * apply once the flush() operation is called.  These persistence operations should
 * be lazily evaluated and will be coerced on flush().  Callers must handle invoking
 * rollback() on error or commit() on final success.
 */
@Stable
public interface GraphListenerRepository extends GraphListener<Node, Edge> {

    /**
     * Execute the queued operations to the persistent store.
     */
    void flush();
}
