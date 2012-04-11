package com.fasterxml.cachemate.raw;

import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.cachemate.CacheEntry;

public class RawCacheElement extends RawCacheElementBase
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

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
	
	public RawCacheElement(int timeToLiveSecs, Hasher keyHasher)
	{
		super(timeToLiveSecs, keyHasher);
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
    	return null;
    }
	
    /*
    /**********************************************************************
    /* Put method(s)
    /**********************************************************************
     */
	
    protected CacheEntry<byte[], byte[]> _putEntry(long currentTime, int timeToLiveQ,
    		byte[] key, int keyHash, byte[] value, int weight)
    {    
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
