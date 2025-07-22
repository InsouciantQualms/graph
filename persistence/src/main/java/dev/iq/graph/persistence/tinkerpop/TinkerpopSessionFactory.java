/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.tinkerpop;

import dev.iq.graph.persistence.Session;
import dev.iq.graph.persistence.SessionFactory;
import java.util.Map;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;

/**
 * Tinkerpop implementation of SessionFactory.
 */
public final class TinkerpopSessionFactory implements SessionFactory {

    @Override
    public Session create() {

        final var graph = GraphFactory.open(Map.of(
                "gremlin.graph", "org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph",
                "gremlin.tinkergraph.defaultVertexPropertyCardinality", "single",
                "gremlin.tinkergraph.allowNullPropertyValues", "true"));
        return new TinkerpopSession(graph);
    }
}
