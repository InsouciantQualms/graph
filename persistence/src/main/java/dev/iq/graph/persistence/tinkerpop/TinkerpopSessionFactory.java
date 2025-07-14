package dev.iq.graph.persistence.tinkerpop;

import dev.iq.common.persist.Session;
import dev.iq.common.persist.SessionFactory;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;

import java.util.Map;

/**
 * Tinkerpop implementation of SessionFactory.
 */
public final class TinkerpopSessionFactory implements SessionFactory {

    @Override
    public Session create() {

        final var graph = GraphFactory.open(
            Map.of(
                "gremlin.graph", "org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph",
                "gremlin.tinkergraph.defaultVertexPropertyCardinality", "single",
                "gremlin.tinkergraph.allowNullPropertyValues", "true"
            )
        );
        return new TinkerpopSession(graph);
    }
}