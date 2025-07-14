package dev.iq.graph.persistence;

import dev.iq.common.adt.Stable;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import org.jgrapht.event.GraphListener;

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
