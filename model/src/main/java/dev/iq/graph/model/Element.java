package dev.iq.graph.model;

import dev.iq.common.adt.Stable;
import dev.iq.common.version.NanoId;
import dev.iq.common.version.Versioned;

import java.util.Set;

@Stable
public interface Element extends Versioned {

    Data data();

    Set<NanoId> components();
}
