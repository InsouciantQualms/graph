/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */


package dev.iq.graph.model.simple;

import dev.iq.graph.model.Data;

/**
 * Simple implementation of Data interface.
 */
public record SimpleData(Class<?> type, Object value) implements Data {}