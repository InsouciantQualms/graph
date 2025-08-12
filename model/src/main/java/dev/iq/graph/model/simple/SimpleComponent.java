/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.simple;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Type;
import java.time.Instant;
import java.util.Optional;

/**
 * Record implementation of Component.  Used to store data about the versioned component.
 */
public record SimpleComponent(Locator locator, Type type, Data data, Instant created, Optional<Instant> expired)
        implements Component {

    /** {@inheritDoc} */
    @Override
    public Component expire(final Instant timestamp) {

        return new SimpleComponent(locator, type, data, created, Optional.of(timestamp));
    }
}
