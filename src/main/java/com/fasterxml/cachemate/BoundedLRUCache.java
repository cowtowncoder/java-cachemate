package com.fasterxml.cachemate;

import java.util.Arrays;

public class BoundedLRUCache<K, V>
{
    // // // Configuration

    protected final KeyConverter<K> _keyConverter;
    
    /**
     * Maximum weight (approximate size) of all entries cache can contain
     */
    protected long _maxWeight;

    /**
     * Maximum number of entries that can be stored in the cache
     */
    protected int _maxEntries;

    /**
     * Length of time entries will remain non-stale in cache after being inserted;
     * measure in units of 256 milliseconds (about quarter of a second). Unit chosen
     * to allow using ints for time calculation.
     */
    protected int _configTimeToLive;
    
    /**
     * Setting that defines how many entries are to be checked for staleness
     * when finding entries; 0 means that no checks are made
     */
    protected int _configInvalidatePerGet = 1;

    /**
     * Setting that defines how many entries are to be checked for staleness
     * when adding an entry (even if no free space is needed); 0 means that no checks would be made
     */
    protected int _configInvalidatePerInsert = 2;
    
    // // // Current load

    /**
     * Total current weight (approximate size) of all keys and entries in
     * the cache.
     */
    protected long _currentWeight;

    /**
     * Total number of entries in the cache
     */
    protected int _currentEntries;
    
    // // // Actual entries

    /**
     * Primary hash area for entries
     */
    protected Entry<K, V>[] _entries;
    
    /**
     * Oldest entry in this cache; needed to efficiently clean up
     * stale entries
     */
    protected Entry<K, V> _oldestEntry;

    // // // Statistics
    
    /**
     * Number of times an entry has been found within cache
     */
    protected int _hitCount;
    
    /**
     * Number of times an entry has been found within cache
     */
    protected int _missCount;

    /**
     * Number of times entries have been inserted in the cache
     */
    protected int _insertCount;
    
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    /**
     * @param timeToLiveSecs Amount of time entries will remain fresh (non-stale) in
     *   cache; in seconds.
     */
    @SuppressWarnings("unchecked")
    public BoundedLRUCache(KeyConverter<K> keyConverter,
            int maxEntries, long maxWeight,
            long timeToLiveSecs)
    {
        _keyConverter = keyConverter;
        _maxEntries = maxEntries;
        _maxWeight = maxWeight;
        // array needs to be a power of two, just find next bigger:
        int size = 16;
        
        while (size < maxEntries) {
            size += size;
        }
        _entries = (Entry<K,V>[]) new Entry<?,?>[size];

        _configTimeToLive = (int) ((1000L * timeToLiveSecs) >> 8);
    }
    
    /*
    /**********************************************************************
    /* Public API, config access, resizing
    /**********************************************************************
     */
    
    // To be implemented!!!
    
    /*
    /**********************************************************************
    /* Public API, basic access
    /**********************************************************************
     */

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
    public Entry<K,V> findEntry(long currentTime, K key, int keyHash)
    {
        int index = (keyHash & (_entries.length - 1));
        // First, locate the entry
        Entry<K,V> entry = _entries[index];
        if (entry != null) {
            while (entry != null) {
                if (_keyConverter.keysEqual(key, entry._key)) {
                    break;
                }
                entry = entry._nextCollision;
            }
        }
        // then, if found, verify it is not stale
        int expireTime = latestStaleTimestamp(currentTime);

        // and finally, if aggressively cleaning up, check if stale entries exist
        int count = _configInvalidatePerGet;
        while (count > 0 && invalidateOldestIfStale(expireTime)) {
            --count;
        }
        return entry;
    }

    /**
     * @param timestamp Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     */
    public Entry<K,V> removeEntry(long timestamp, K key, int keyHash)
    {
        // !!! TBI
        return null;
    }

    /**
     * @param timestamp Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Existing value for the key, if any; usually null but could be non-null
     *    for race condition cases
     */
    public Entry<K,V> putEntry(long timestamp, K key, int keyHash,
            V value, int weight)
    {
        // !!! TBI
        return null;
    }
    
    public void removaAll()
    {
        // Easy enough to drop all:
        _oldestEntry = null;
        Arrays.fill(_entries, null);
        _currentWeight = 0L;
        _currentEntries = 0;
        // but do not clear stats necessarily
    }
    
    /*
    /**********************************************************************
    /* Public API, stats
    /**********************************************************************
     */

    public CacheStats getStats() {
        return new CacheStats(_hitCount, _missCount, _insertCount,
                _currentEntries, _currentWeight,
                _maxEntries, _maxWeight);
    }

    public void clearStats()
    {
        _hitCount = 0;
        _missCount = 0;
        _insertCount = 0;
    }

    /**
     * Method that can be used to decay existing statistics, to keep
     * smoothed-out average of hit/miss ratios over time.
     */
    public void decayStats(double ratio)
    {
        _hitCount = (int) (_hitCount * ratio);
        _missCount = (int) (_missCount * ratio);
        _insertCount = (int) (_insertCount * ratio);
    }
    
    /*
    /**********************************************************************
    /* Public methods, other
    /**********************************************************************
     */

    /**
     * Helper method that will return internal timestamp value (in units of
     * 256 milliseconds, i.e. quarter second) of the latest timepoint
     * that is stale.
     */
    public int latestStaleTimestamp(long currentTimeMsecs)
    {
        // First, convert current time to units of ~1/4 second
        int currentTime = (int) (currentTimeMsecs >> 8);
        // then go back by time-to-live time units:
        currentTime -= _configTimeToLive;
        return currentTime;
    }

    /**
     * Method for invalidating all stale entries this cache has (if any)
     * 
     * @param currentTimeMsecs Logical timestamp when this operation occurs
     * 
     * @return Number of stale entries invalidated
     */
    public int invalidateStale(long currentTimeMsecs)
    {
        return invalidateStale(currentTimeMsecs, Integer.MAX_VALUE);
    }
    
    public int invalidateStale(long currentTimeMsecs, int maxToInvalidate)
    {
        int count = 0;
        int earliestNonStale = latestStaleTimestamp(currentTimeMsecs);
        
        while (count < maxToInvalidate && invalidateOldestIfStale(earliestNonStale)) {
            ++count;
        }
        return count;
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    /**
     * Method that will delete the oldest entry in the cache, if there
     * is at least one entry in the cache, and it was inserted at or
     * before given timepoint.
     */
    protected boolean invalidateOldestIfStale(int latestStaleTime)
    {
        Entry<K,V> oldest = _oldestEntry;
        if (oldest != null) {
            /* ok now: timestamps we use are relative (due to truncation), so
             * we MUST compare by subtraction, compare difference
             */
            int diff = oldest._insertTime - latestStaleTime;
            if (diff <= 0) { // created at or before expiration time (latest timestamp that is stale)
                --_currentEntries;
                _currentWeight -= oldest._weight;
                _oldestEntry = _oldestEntry._nextNewer;
                 return true;
            }
        }
        return false;
    }

    protected boolean invalidateOldest()
    {
        Entry<K,V> oldest = _oldestEntry;
        if (oldest != null) {
            --_currentEntries;
            _currentWeight -= oldest._weight;
            _oldestEntry = _oldestEntry._nextNewer;
             return true;
        }
        return false;
    }
    
    /*
    /**********************************************************************
    /* Helper classes
    /**********************************************************************
     */

    /**
     * Entry for cache; contains both key and value, to reduce number
     * of object instances needed. Also provides metadata useful for
     * caller.
     */
    public final static class Entry<K, V>
    {
        /**
         * Entry key
         */
        public final K _key;

        /**
         * Hash code of the key value (since it is possible
         * that hashCode of key itself is not useful, as is the case
         * for byte[])
         */
        public final int _keyHash;

        /**
         * Entry value;
         */
        public final V _value;

        /**
         * Timepoint when entry was added in cache, in units of 256 milliseconds
         * (about quarter of a second). Used for staleness checks.
         */
        public final int _insertTime;
        
        /**
         * Weight of this entry, including both key and value
         */
        public final int _weight;
        
        /**
         * Link to entry added after current entry; null if this is the newest
         * entry
         */
        public Entry<K, V> _nextNewer;
        
        /**
         * Link to next entry in collision list; will have hash code that resulted
         * in same bucket as this entry. Null if no collisions for bucket
         */
        public final Entry<K, V> _nextCollision;

        /**
         * Number of times this entry has been succesfully retrieved from
         * the cache; may be used to decide if entry is to be promoted/demoted,
         * in addition to basic LRU ordering
         */
        public int _timesReturned;
        
        public Entry(K key, int keyHash, V value, int insertTime, int weight,
                Entry<K,V> nextCollision)
        {
            _key = key;
            _value = value;
            _keyHash = keyHash;
            _insertTime = insertTime;
            _weight = weight;
            _nextCollision = nextCollision;
        }

        public void linkNextNewer(Entry<K,V> next)
        {
            if (_nextNewer != null) { // sanity check
                throw new IllegalStateException("Already had entry with key "+_key+" (hash code 0x"+Integer.toHexString(_keyHash)+")");
            }
            _nextNewer = next;
        }
    }
}
