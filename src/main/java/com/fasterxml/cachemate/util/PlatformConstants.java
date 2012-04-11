package com.fasterxml.cachemate.util;

/**
 * Container for constants used for things like estimating rough
 * memory usage. If we wanted more accurate estimations, we would need
 * to try to determine platform properties (32 vs 64 bit architecture),
 * but for now we don't care too much since heaviest memory usage is
 * assumed to come from byte arrays that we can estimate more accurately.
 */
public interface PlatformConstants
{
    /**
     * Conservative estimation of memory usage of an empty (no additional
     * fields beyond <code>Object.class</code>) Object instance.
     */
    public final static int BASE_OBJECT_MEMORY_USAGE = 16;

    /**
     * Let's also assume 4 bytes per field base memory usage (which is
     * what 32-bit systems use).
     */
    public final static int BASE_FIELD_MEMORY_USAGE = 4;
}
