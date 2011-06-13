package com.fasterxml.cachemate;

/**
 * Entry for cache; contains both _key and value, to reduce number
 * of object instances needed. Also provides metadata useful for
 * caller.ache
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
     */
    public long getAgeInMilliSeconds(long currentTime);
}
