package com.fasterxml.cachemate.raw;

import java.nio.ByteBuffer;

/**
 * Lowest level "raw" storage entity, backed by a slice of a physical
 * {@link ByteBuffer}, structured in a way that allows both
 * reads and writes. This means that read access is not necessarily
 * optimal, due to conflicting needs between puts and gets.
 * At any given point, only one such instance should ever be active;
 * and once slab fills (or time quota it is used for expires),
 * instance will be converted into a {@link ReadOnlySlab}.
 */
public class WritableSlab
{
}
