/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.SimpleVersionedFinder;
import dev.iq.common.version.VersionedFinder;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.GraphSpace;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.View;
import org.jgrapht.Graph;
import org.jgrapht.ListenableGraph;
import org.jgrapht.event.GraphListener;
import org.jgrapht.graph.DefaultListenableGraph;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * JGraphT implementation of GraphSpace interface.
 * Serves as a container for all graph elements and operations.
 */
public final class JGraphtGraphSpace implements GraphSpace {

    /** JGraphT delegated in memory graph. */
    private final Graph<Node, Edge> graph;

    /** Component operations. */
    private final SimpleVersionedFinder<Component> components;

    /** Creates a new graph with a listener attached. */
    public JGraphtGraphSpace(final GraphListener<Node, Edge> listener) {

        final Graph<Node, Edge> base = new DirectedMultigraph<>(null, null, false);
        final ListenableGraph<Node, Edge> wrapper = new DefaultListenableGraph<>(base);
        wrapper.addGraphListener(listener);
        graph = wrapper;
        components = new SimpleVersionedFinder<>();
    }

    /** {@inheritDoc} */
    @Override
    public VersionedFinder<Component> components() {

        return components;
    }

    /** {@inheritDoc} */
    @Override
    public View view() {

        return new JGraphtGraphView(graph);
    }
}
