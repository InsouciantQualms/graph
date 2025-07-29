package dev.iq.graph.model.lazy;

import dev.iq.common.adt.Lazy;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class LazyComponent implements Component {

    private final Lazy<Component> component;

    public LazyComponent(final Locator locator, final VersionedRepository<Component> repository) {

        component = LazyFactory.of(locator, repository);
    }

    @Override
    public List<Element> elements() {

        return component.get().elements();
    }

    @Override
    public Data data() {

        return component.get().data();
    }

    @Override
    public Locator locator() {

        return component.get().locator();
    }

    @Override
    public Instant created() {

        return component.get().created();
    }

    @Override
    public Optional<Instant> expired() {

        return component.get().expired();
    }
}