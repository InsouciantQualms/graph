/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.jgrapht;

import dev.iq.common.version.Locator;
import dev.iq.common.version.Uid;
import dev.iq.common.version.VersionedFinder;
import dev.iq.common.version.Versions;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jgrapht.Graph;

/**
 * JGraphT-based implementation of immutable node operations for versioned graph elements.
 * This class provides query-only operations on nodes.
 */
public final class JGraphtNodeFinder implements VersionedFinder<Node> {

    /** Delegate graph. */
    private final Graph<Node, Edge> graph;

    /** Creates a new node finder. */
    public JGraphtNodeFinder(final Graph<Node, Edge> graph) {
        this.graph = graph;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Node> findActive(final Uid id) {
        return Versions.findActive(id, graph.vertexSet());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Node> findAt(final Uid id, final Instant timestamp) {
        return Versions.findAt(id, timestamp, graph.vertexSet());
    }

    /** {@inheritDoc} */
    @Override
    public Node find(final Locator locator) {
        return graph.vertexSet().stream()
                .filter(n -> n.locator().equals(locator))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + locator));
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> findVersions(final Uid id) {

        return Versions.findAllVersions(id, graph.vertexSet());
    }
}
