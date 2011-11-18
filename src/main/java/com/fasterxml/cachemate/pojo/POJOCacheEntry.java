package com.fasterxml.cachemate.pojo;

import com.fasterxml.cachemate.CacheEntry;

/**
 * {@link CacheEntry} implementation for cache elements that use
 * a single _key (hence, "simple")
 */
public class POJOCacheEntry<K, V>
    extends POJOCacheEntryBase<K, V, POJOCacheEntry<K, V>>
    implements CacheEntry<K, V>
{
    // // // Constants
    
    /**
     * This is our guestimation of per-entry base overhead JVM incurs; it is used
     * to get closer approximation of true memory usage of cache structure.
     * We will use 16 bytes for base object, and otherwise typical 32-bit system
     * values for 11 fields we have. This gives estimation of 60 bytes; not
     * including referenced objects (_key, value)
     */
    public final static int MEM_USAGE_PER_ENTRY = 16 + (11 * 4);

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */
    
    /**
     * Constructors used for constructing placeholder instances (heads and tails
     * of linked lists)
     */
    public POJOCacheEntry() {
        this(null, 0, null, 0, 0, null);
    }
    
    public POJOCacheEntry(K key, int keyHash, V value, int expirationTime, int weight,
            POJOCacheEntry<K,V> nextCollision)
    {
        super(key, keyHash, value, expirationTime, weight, nextCollision);
    }

    /*
    /**********************************************************************
    /* Standard method overrides
    /**********************************************************************
     */
    
    @Override
    public final String toString() {
        return new StringBuilder().append(getKey()).append(':').append(getValue()).toString();
    }
}
