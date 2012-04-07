/**
 * Package that contains Cache implementations that store "raw"
 * entries, where keys and values are basic byte sequences.
 * The benefit is that such entries are relatively compact, and
 * can be stored in raw byte arrays as well as outside of heap
 * using {@link java.nio.ByteBuffer}s.
 * Eviction strategy is typically simple FIFO; and additions are
 * append-only as a consequence.
 */
package com.fasterxml.cachemate.raw;
