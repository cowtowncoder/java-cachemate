package com.fasterxml.cachemate.pojo;

import com.fasterxml.cachemate.CacheElement;
import com.fasterxml.cachemate.CacheEntry;
import com.fasterxml.cachemate.CacheStats;

/**
 * Base class for various POJO cache elements; components that
 * store actual Java objects as-is (without serialization to bytes).
 */
abstract class POJOCacheElementBase<K, V, E extends CacheEntry<K,V>>
    implements CacheElement<K, V>
{
    /*
    /**********************************************************************
    /* Configuration, size/time-to-live limits
    /**********************************************************************
     */
    
    /**
     * Maximum weight (approximate size) of all entries cache can contain.
     * Set to maximum weight allowed minus overhead of the cache structure
     * itself.
     */
    protected long _maxContentsWeight;

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

    /*
    /**********************************************************************
    /* Configuration, other
    /**********************************************************************
     */
    
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

    /*
    /**********************************************************************
    /* Information on current contents
    /**********************************************************************
     */

    /**
     * Total current weight (approximate size) of all keys and entries in
     * the cache.
     */
    protected long _currentContentsWeight;

    /**
     * Total number of entries in the cache
     */
    protected int _currentEntries;

    /**
     * Placeholder entry that represents the "old" end of
     * linkage; oldest and least-recently used entries (accessible
     * via entry links)
     */
    protected E _oldEntryHead;

    /**
     * Placeholder entry that represents the "new" end of
     * linkage; newest and most-recently used entries (accessible
     * via entry links)
     */
    protected E _newEntryHead;
 
    /*
    /**********************************************************************
    /* Statistics: access stats
    /**********************************************************************
     */
    
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
    /* Life-cycle
    /**********************************************************************
     */

    protected POJOCacheElementBase() { }
    
    /*
    /**********************************************************************
    /* Public API, config access, resizing
    /**********************************************************************
     */

    /*
    // More to be added:
     */

    public int getConfigInvalidatePerGet() {
        return _configInvalidatePerGet;
    }

    public void setConfigInvalidatePerGet(int value) {
        _configInvalidatePerGet = value;
    }

    /*
    /**********************************************************************
    /* Public methods, invalidation
    /**********************************************************************
     */

    @Override
    public int invalidateStale(long currentTimeMsecs) {
        return invalidateStale(currentTimeMsecs, Integer.MAX_VALUE);
    }

    @Override
    public abstract int invalidateStale(long currentTimeMsecs, int maxToInvalidate);
    
    /*
    /**********************************************************************
    /* Public API, stats
    /**********************************************************************
     */

    @Override
    public final int size() {
        return _currentEntries;
    }

    /**
     * Returns crude estimated memory usage for entries cache contains, not
     * including overhead of cache itself (which is small); slightly
     * lower than what {@link #weight} would return.
     */
    @Override
    public final long contentsWeight() { return _currentContentsWeight; }

    /**
     * Returns crude estimated memory usage for the cache as whole, including
     * contents
     */
    @Override
    public abstract long weight();
    
    public CacheStats getStats() {
        return new CacheStats(_hitCount, _missCount, _insertCount,
                size(), weight(),
                _maxEntries, _maxContentsWeight);
    }

    @Override
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
    @Override
    public void decayStats(double ratio)
    {
        _hitCount = (int) (_hitCount * ratio);
        _missCount = (int) (_missCount * ratio);
        _insertCount = (int) (_insertCount * ratio);
    }
}

