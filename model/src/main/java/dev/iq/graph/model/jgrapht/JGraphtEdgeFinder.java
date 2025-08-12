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
 * JGraphT-based implementation of immutable edge operations for versioned graph elements.
 * This class provides query-only operations on edges.
 */
public final class JGraphtEdgeFinder implements VersionedFinder<Edge> {

    /** Delegate graph. */
    private final Graph<Node, Edge> graph;

    /** Creates a new edge finder. */
    public JGraphtEdgeFinder(final Graph<Node, Edge> graph) {

        this.graph = graph;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Edge> findActive(final Uid id) {

        return Versions.findActive(id, graph.edgeSet());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Edge> findAt(final Uid id, final Instant timestamp) {

        return Versions.findAt(id, timestamp, graph.edgeSet());
    }

    /** {@inheritDoc} */
    @Override
    public Edge find(final Locator locator) {

        return graph.edgeSet().stream()
                .filter(e -> e.locator().equals(locator))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + locator));
    }

    /** {@inheritDoc} */
    @Override
    public List<Edge> findVersions(final Uid id) {

        return Versions.findAllVersions(id, graph.edgeSet());
    }
}
