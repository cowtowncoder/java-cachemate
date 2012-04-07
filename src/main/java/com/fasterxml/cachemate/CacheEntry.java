package com.fasterxml.cachemate;

/**
 * Entry object for in-memory Object cache, as well as general return entry
 * for all {@link CacheElement}s.
 * Contains both _key and value, to reduce number of object instances to be passed around.
 * Also provides metadata useful for caller.
 */
public interface CacheEntry<K, V>
{
    /*
    /**********************************************************************
    /* Public accessors for key, value
    /**********************************************************************
     */

    /**
     * Accessor for getting the primary key of the entry
     */
    public K getKey();

    /**
     * Accessor for hash value of the primary key
     */
    public int getKeyHash();

    /**
     * Accessor for value this entry contains
     */
    public V getValue();

    /**
     * Accessor for logical weight of this entry, used for determining
     * size constraints. Weight includes both key and value weights.
     */
    public int getWeight();
    
    /*
    /**********************************************************************
    /* Public accessors for metadata
    /**********************************************************************
     */
    
    /**
     * Accessor for checking approximate age of the entry (in milliseconds)
     * 
     * @param currentTime Current time as reported by
     *    {link {@link System#currentTimeMillis()}
     *    
     * @deprecated Since 0.5 replaced by {@link #getExpirationInMilliSeconds(long)}
     */
//    public long getAgeInMilliSeconds(long currentTime);

    /**
     * Accessor for getting an estimate of how long this entry may still be
     * retained (and accessible) by the cache element.
     * Can be used for things like refreshers that try to re-load new value
     * before expiration.
     * 
     * Note that some backends may not be able to provide accurate estimate;
     * possibly not even any. If so, any value between initial TTL and 0L is
     * acceptable return value
     * 
     * @param currentTime Current time as reported by
     *    {link {@link System#currentTimeMillis()}
     *    (or provided as part of testing, simulation)
     * 
     * @return Approximate time until entry expires, in milliseconds.
     * 
     * @since 0.5.0
     */
    public long getExpirationInMilliSeconds(long currentTime);
}
