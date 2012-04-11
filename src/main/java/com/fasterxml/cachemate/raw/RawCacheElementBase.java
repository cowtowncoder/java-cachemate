package com.fasterxml.cachemate.raw;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.cachemate.CacheElement;
import com.fasterxml.cachemate.CacheEntry;
import com.fasterxml.cachemate.CacheStats;
import com.fasterxml.cachemate.util.TimeUtil;

/**
 * Shared abstract base class that defines common API for
 * raw cache elements; variation being mostly to support
 * multi-key variants.
 */
public abstract class RawCacheElementBase
    implements CacheElement<byte[], byte[]>
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

	/**
	 * Object used for calculating hash code for keys.
	 */
	protected final Hasher _keyHasher;
	
	/**
     * Default length of time entries will remain non-stale in cache after being inserted;
     * measured in units of 256 milliseconds (about quarter of a second).
     * Unit chosen  to allow using ints for time storage and calculation.
     */
    protected final int _configTimeToLive;

    /*
    /**********************************************************************
    /* Write-syncing
    /**********************************************************************
     */

	/**
	 * We will use a fair binary semaphore for mutual exclusion of
	 * all writes.
	 */
	protected final Semaphore _writeLock = new Semaphore(1, true);
	
    /*
    /**********************************************************************
    /* Statistics, cache size
    /**********************************************************************
     */
    
    /**
     * Number of entries in all slabs; incrementally updated with regular
     * inserts, but recalculated when rolling out old slab(s).
     *<p>
     * NOTE: approximate count, since duplicates are allowed, and tombstones
     * are typically retained.
     */
    protected final AtomicInteger _entryCount = new AtomicInteger();

    /**
     * Estimated size of raw keys and entries; does not include memory used
     * for indexes, or overhead.
     */
    protected final AtomicLong _weightContent = new AtomicLong();

    /**
     * Estimated total in-use memory amount, including keys, values and
     * indexes. Does not, however, include all the memory that may be
     * allocated for empty slabs.
     */
    protected final AtomicLong _weightTotal = new AtomicLong();

    /*
    /**********************************************************************
    /* Statistics: access stats
    /**********************************************************************
     */
    
    /**
     * Number of times an entry has been found within cache
     */
    protected final AtomicInteger _hitCount = new AtomicInteger();
    
    /**
     * Number of times an entry has been found within cache
     */
    protected final AtomicInteger _missCount = new AtomicInteger();

    /**
     * Number of times entries have been inserted in the cache
     */
    protected final AtomicInteger _insertCount = new AtomicInteger();
    
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    /**
     * @param timeToLiveSecs Default TTL for entries
     * @param hasher Object that defines hash code calculation mechanism used
     *   for raw keys
     */
    
    protected RawCacheElementBase(int timeToLiveSecs, Hasher keyHasher)
    {
        _configTimeToLive = TimeUtil.secondsToInternal((int) timeToLiveSecs);
        _keyHasher = keyHasher;
    }
    
    /*
    /**********************************************************************
    /* Simple accessors
    /**********************************************************************
     */
    
    @Override
    public int size() {
    	return _entryCount.get();
    }

    @Override
    public long contentsWeight() {
    	return _weightContent.get();
    }

    @Override
    public long weight() {
        return _weightTotal.get();
    }

    /*
    /**********************************************************************
    /* Put methods
    /**********************************************************************
     */
    
    @Override
    public final CacheEntry<byte[], byte[]> putEntry(long currentTime,
    		byte[] key, byte[] value, int weight) {
        return _putEntry(currentTime, _configTimeToLive,
                key, _keyHasher.calcHash(key, 0, key.length), value, weight);
    }
    
    @Override
    public CacheEntry<byte[], byte[]> putEntry(long currentTime, int timeToLiveSecs,
    		byte[] key, byte[] value, int weight) {
        return _putEntry(currentTime, TimeUtil.secondsToInternal(timeToLiveSecs),
                key, _keyHasher.calcHash(key, 0, key.length), value, weight);
    }
    
    @Override
    public final CacheEntry<byte[], byte[]> putEntry(long currentTime,
    		byte[] key, int keyHash, byte[] value, int weight)
    {
        return _putEntry(currentTime, _configTimeToLive, key, keyHash, value, weight);
    }

    @Override
    public final CacheEntry<byte[], byte[]> putEntry(long currentTime, int timeToLiveSecs,
    		byte[] key, int keyHash, byte[] value, int weight)
    {
        return _putEntry(currentTime, TimeUtil.secondsToInternal(timeToLiveSecs),
        		key, keyHash, value, weight);
    }
    
    protected abstract CacheEntry<byte[], byte[]> _putEntry(long currentTime, int timeToLiveQ,
    		byte[] key, int keyHash, byte[] value, int weight);

    /*
    /**********************************************************************
    /* Get methods
    /**********************************************************************
     */
    
    @Override
    public final CacheEntry<byte[], byte[]> findEntry(long currentTime, byte[] key) {
    	return findEntry(currentTime, key, _keyHasher.calcHash(key, 0, key.length));
    }

    @Override
    public abstract CacheEntry<byte[], byte[]> findEntry(long currentTime, byte[] key,
            int keyHash);

    /*
    /**********************************************************************
    /* Removals
    /**********************************************************************
     */
    
    @Override
    public CacheEntry<byte[], byte[]> removeEntry(long currentTime, byte[] key) {
        return removeEntry(currentTime, key, _keyHasher.calcHash(key, 0, key.length));
    }

    @Override
    public abstract CacheEntry<byte[], byte[]> removeEntry(long currentTime, byte[] key,
            int keyHash);

    @Override
    public abstract void removeAll();

    /**
     * Since entries are not individually invalidated (rather, complete slabs
     * are removed either to make room, or to drop fully stale slab), there is
     * nothing to do here; will always return 0.
     */
    @Override
    public int invalidateStale(long currentTimeMsecs) {
        return 0;
    }

    /**
     * Since entries are not individually invalidated (rather, complete slabs
     * are removed either to make room, or to drop fully stale slab), there is
     * nothing to do here; will always return 0.
     */
    @Override
    public int invalidateStale(long currentTimeMsecs, int maxToInvalidate) {
        return 0;
    }

    /*
    /**********************************************************************
    /* Statistics methods
    /**********************************************************************
     */
    
    @Override
    public CacheStats getStats()
    {
    	return new CacheStats(_hitCount.get(), _missCount.get(), _insertCount.get(),
                size(), weight(), contentsWeight(),
                -1, // no entry count limit
                // TODO: maxTotalWeight?
                -1L);
    }

    @Override
    public void clearStats()
    {
    	_hitCount.set(0);
    	_missCount.set(0);
    	_insertCount.set(0);
    }

    @Override
    public void decayStats(double ratio)
    {
        _hitCount.set((int) (_hitCount.get() * ratio));
        _missCount.set((int) (_missCount.get() * ratio));
        _insertCount.set((int) (_insertCount.get() * ratio));
    }
}
