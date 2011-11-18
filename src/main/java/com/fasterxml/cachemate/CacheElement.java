package com.fasterxml.cachemate;

/**
 * Interface for "simple" cache elements that map a single _key to
 * a value. Serves as the base for multi-_key variants, as
 * well as for concrete single-_key implementations.
 * 
 * @author Tatu Saloranta
 */
public interface CacheElement<K, V>
{
    /*
    /**********************************************************************
    /* Public API, basic access (put, find, delete)
    /**********************************************************************
     */

    /**
     * Method for putting specified entry in this cache; if an entry with the key
     * exists, it will be replaced.
     * Note that time-to-live used is the default TTL.
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to insert
     * @param value Value for the entry to insert
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Previous value for the key, if any; usually null but could be non-null
     *    for race condition cases
     */
    public CacheEntry<K,V> putEntry(long currentTime, K key, V value, int weight);

    /**
     * Method for putting specified entry in this cache; if an entry with the key
     * exists, it will be replaced.
     * Note that time-to-live used is the default TTL.
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param timeToLiveSecs TTL value to use for this particular entry, instead of
     *   the default TTL
     * @param key Key of the entry to insert
     * @param value Value for the entry to insert
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Previous value for the key, if any; usually null but could be non-null
     *    for race condition cases
     * 
     * @since 0.5.0
     */
    public CacheEntry<K,V> putEntry(long currentTime, int timeToLiveSecs, K key, V value, int weight);
    
    /**
     * Method for putting specified entry in this cache; if an entry with the key
     * exists, it will be replaced.
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to insert
     * @param keyHash Hash code for the key
     * @param value Value for the entry to insert
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Previous value for the key, if any; usually null but could be non-null
     *    for race condition cases
     */
    public CacheEntry<K,V> putEntry(long currentTime, K key, int keyHash,
            V value, int weight);

    /**
     * Method for putting specified entry in this cache; if an entry with the key
     * exists, it will be replaced.
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param timeToLiveSecs TTL value to use for this particular entry, instead of
     *   the default TTL
     * @param key Key of the entry to insert
     * @param keyHash Hash code for the key
     * @param value Value for the entry to insert
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Previous value for the key, if any; usually null but could be non-null
     *    for race condition cases
     *    
     * @since 0.5.0
     */
    public CacheEntry<K,V> putEntry(long currentTime, int timeToLiveSecs,
            K key, int keyHash, V value, int weight);
    
    /**
     * Method for finding entry with specified key from this cache element;
     * returns null if no such entry exists; otherwise found entry
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     */
    public CacheEntry<K,V> findEntry(long currentTime, K key);

    /**
     * Method for finding entry with specified key from this cache element;
     * returns null if no such entry exists; otherwise found entry
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     */
    public CacheEntry<K,V> findEntry(long currentTime, K key, int keyHash);

    /**
     * Method for trying to remove entry with specified key. Returns removed
     * entry, if one found; otherwise returns null
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     */
    public CacheEntry<K,V> removeEntry(long currentTime, K key);

    /**
     * Method for trying to remove entry with specified key. Returns removed
     * entry, if one found; otherwise returns null
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     */
    public CacheEntry<K,V> removeEntry(long currentTime, K key, int keyHash);

    /**
     * Method for clearing up the cache by removing all entries.
     */
    public void removeAll();
    
    /*
    /**********************************************************************
    /* Public methods, invalidation
    /**********************************************************************
     */

    /**
     * Method for invalidating all stale entries this cache has (if any)
     * 
     * @param currentTimeMsecs Logical timestamp when this operation occurs
     * 
     * @return Number of stale entries invalidated
     */
    public int invalidateStale(long currentTimeMsecs);

    /**
     * Method for invalidating up to specified number of stale entries
     * this cache has (if any)
     * 
     * @param currentTimeMsecs Logical timestamp when this operation occurs
     * @param maxToInvalidate Maximum entries to invalidate
     * 
     * @return Number of stale entries invalidated
     */
    public int invalidateStale(long currentTimeMsecs, int maxToInvalidate);
    
    /*
    /**********************************************************************
    /* Public API, stats
    /**********************************************************************
     */

    /**
     * Method for accessing number of entries in this element. Count may
     * contain stale entries, as no invalidation is performed;
     * most recently updated entry count is returned as is
     * (to force 
     */
    public int size();

    /**
     * Returns crude estimated memory usage for entries cache contains, not
     * including overhead of cache itself (which is small); slightly
     * lower than what {@link #weight} would return.
     */
    public long contentsWeight();

    /**
     * Returns crude estimated memory usage for the cache as whole, including
     * contents
     */
    public long weight();
    
    /**
     * Method for accessing currently cache access statistics.
     */
    public CacheStats getStats();

    /**
     * Method for clearing all cache statistics.
     */
    public void clearStats();

    /**
     * Method that can be used to decay existing statistics, to keep
     * smoothed-out average of hit/miss ratios over time.
     */
    public void decayStats(double ratio);
}
