package dev.iq.graph.model.lazy;

import dev.iq.common.adt.Lazy;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public final class LazyEdge implements Edge {

    private final Lazy<Edge> edge;

    public LazyEdge(final Locator locator, final VersionedRepository<Edge> repository) {

        edge = LazyFactory.of(locator, repository);
    }

    @Override
    public Node source() {

        return edge.get().source();
    }

    @Override
    public Node target() {

        return edge.get().target();
    }

    @Override
    public Type type() {

        return edge.get().type();
    }

    @Override
    public Data data() {

        return edge.get().data();
    }

    @Override
    public Set<NanoId> components() {

        return edge.get().components();
    }

    @Override
    public Locator locator() {

        return edge.get().locator();
    }

    @Override
    public Instant created() {

        return edge.get().created();
    }

    @Override
    public Optional<Instant> expired() {

        return edge.get().expired();
    }
}