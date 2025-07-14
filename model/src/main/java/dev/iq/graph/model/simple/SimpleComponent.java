package dev.iq.graph.model.simple;

import dev.iq.common.version.Locator;
import dev.iq.graph.model.Component;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Element;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record SimpleComponent(
    Locator locator,
    List<Element> elements,
    Data data,
    Instant created,
    Optional<Instant> expired
) implements Component {}
