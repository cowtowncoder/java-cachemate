package com.fasterxml.cachemate.raw;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.cachemate.CacheEntry;
import com.fasterxml.cachemate.util.TimeUtil;

public class RawCacheElement extends RawCacheElementBase
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    /**
     * Actual full {@link ByteBuffer}, split into slabs.
     */
    protected final ByteBuffer _allData;
    
    /**
     * Currently actively read/write slab; if available. Note that it will
     * be null for brief periods of time, when currently active slab is
     * being frozen; so callers need to be able to handle this case by
     * skipping to read-only slabs.
     */
    protected final AtomicReference<WritableSlab> _writableSlab = new AtomicReference<WritableSlab>();

    /**
     * Pointed to the first (and highest-priority) "frozen" slab. Access needs
     * to be handled in this order. Reference will never be null.
     */
    protected final AtomicReference<ReadOnlySlab> _firstReadOnlySlab = new AtomicReference<ReadOnlySlab>();
	
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */
	
    public RawCacheElement(int timeToLiveSecs, Hasher keyHasher,
            ByteBuffer buffer)
    {
        super(timeToLiveSecs, keyHasher);
        _allData = buffer;
    }

    /*
    /**********************************************************************
    /* Find method(s)
    /**********************************************************************
     */

    @Override
    public CacheEntry<byte[], byte[]> findEntry(long currentTime, byte[] key,
            int keyHash)
    {
        // First: do we have writable slab (not during roll-over); does it have entry?
        WritableSlab ws = _writableSlab.get();
        if (ws != null) {
            EntryReference entry = ws.findEntry(_allData, key, keyHash);
            // found?
            if (entry != null) {
                return _notStale(_allData, currentTime, entry);
            }
        }
        // if not, maybe in readable slabs?
        ReadOnlySlab slab = _firstReadOnlySlab.get();
        // note: we always have at least one read-only slab
        ReadOnlySlab next;
        
        // only last slab needs access sync, so:
        for (; (next = slab.nextSlab()) != null; slab = next) {
            EntryReference entry = slab.findEntry(_allData, key, keyHash);
            // found?
            if (entry != null) {
                return _notStale(_allData, currentTime, entry);
            }
        }
        // Ok; nothing found, need extra sync for last one
        // TODO: extra sync
        EntryReference entry = slab.findEntry(_allData, key, keyHash);
        // found?
        if (entry != null) {
            return _notStale(_allData, currentTime, entry);
        }
        
    	return null;
    }

    private final CacheEntry<byte[], byte[]> _notStale(ByteBuffer data,
            long currentTime, EntryReference entry)
    {
        // stale? Compare current time (converted to timestamp) to expiry time
        int currTimestamp = TimeUtil.timeToTimestamp(currentTime);
        // timestamp stored is expiry time; which should be in future
        int timeLeft = entry.getTimestamp() - currTimestamp;
        if (timeLeft < 0) { // stale; return null to indicate no match
            return null;
        }
        // TODO: maybe reuse key caller gave?
        return entry.asCacheEntry(currentTime);
    }
    
    /*
    /**********************************************************************
    /* Put method(s)
    /**********************************************************************
     */
	
    @Override
    protected CacheEntry<byte[], byte[]> _putEntry(long currentTime, int timeToLiveQ,
    		byte[] key, int keyHash, byte[] value, int weight)
    {    
        /* To keep things simple, we will use a simple global (within context
         * of raw cache element) write lock; this guards appends to sequential
         * entry area, as well as roll overs.
         * Further locking may be used for individual sub-components, to handle
         * reader/writer race conditions.
         */
    	try {
    	    _writeLock.acquire();
    	} catch (InterruptedException e) {
    	    throw new RuntimeException(e);
    	}
    	try {
    	    // !!! TODO -- implement
    	} finally {
    	    _writeLock.release();
    	}
    	return null;
    }

    /*
    /**********************************************************************
    /* Remove, invalidation
    /**********************************************************************
     */

    @Override
    public CacheEntry<byte[], byte[]> removeEntry(long currentTime, byte[] key,
            int keyHash)
    {
    	return null;
    }

    @Override
    public void removeAll()
    {
    	// !!! TODO
    }
    
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */
}
