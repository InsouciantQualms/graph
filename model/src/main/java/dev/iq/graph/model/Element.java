/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */
package dev.iq.graph.model;

import dev.iq.common.annotation.Stable;
import dev.iq.common.version.Versioned;
import java.util.Set;

@Stable
public interface Element extends Versioned {

    Type type();

    Data data();

    Set<Reference<Component>> components();
}
