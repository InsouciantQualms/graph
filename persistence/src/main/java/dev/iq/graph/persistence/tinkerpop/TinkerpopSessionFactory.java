/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


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