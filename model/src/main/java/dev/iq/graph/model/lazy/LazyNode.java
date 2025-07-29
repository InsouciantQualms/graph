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
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LazyNode implements Node {

    private final Lazy<Node> node;

    public LazyNode(final Locator locator, final VersionedRepository<Node> repository) {

        node = LazyFactory.of(locator, repository);
    }

    @Override
    public List<Edge> edges() {

        return node.get().edges();
    }

    @Override
    public Type type() {

        return node.get().type();
    }

    @Override
    public Data data() {

        return node.get().data();
    }

    @Override
    public Set<NanoId> components() {

        return node.get().components();
    }

    @Override
    public Locator locator() {

        return node.get().locator();
    }

    @Override
    public Instant created() {

        return node.get().created();
    }

    @Override
    public Optional<Instant> expired() {

        return node.get().expired();
    }
}
