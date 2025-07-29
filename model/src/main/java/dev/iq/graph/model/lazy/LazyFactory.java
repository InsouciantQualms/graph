package dev.iq.graph.model.lazy;

import dev.iq.common.adt.Lazy;
import dev.iq.common.persist.VersionedRepository;
import dev.iq.common.version.Locator;
import dev.iq.common.version.Versioned;

final class LazyFactory {

    private LazyFactory() {}

    public static <T extends Versioned> Lazy<T> of(final Locator locator, final VersionedRepository<T> repository) {

        return Lazy.of(() -> repository
                .find(locator)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + locator))
        );
    }
}
