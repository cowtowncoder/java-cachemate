package com.fasterxml.cachemate.raw;

import java.nio.ByteBuffer;

import com.fasterxml.cachemate.CacheElement;
import com.fasterxml.cachemate.CacheEntry;
import com.fasterxml.cachemate.CacheStats;

/**
 * Shared abstract base class that defines common API for
 * raw cache elements; variation being mostly to support
 * multi-key variants.
 */
public class RawCacheElementBase
    implements CacheElement<byte[], byte[]>
{
    /**
     * The whole backing buffer that is split in slabs.
     */
    protected final ByteBuffer _dataBuffer;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    /*
    /**********************************************************************
    /* Simple accessors
    /**********************************************************************
     */
    
    @Override
    public int size() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long contentsWeight() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long weight() {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
    /**********************************************************************
    /* Put methods
    /**********************************************************************
     */
    
    @Override
    public CacheEntry<byte[], byte[]> putEntry(long currentTime, byte[] key,
            byte[] value, int weight) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CacheEntry<byte[], byte[]> putEntry(long currentTime,
            int timeToLiveSecs, byte[] key, byte[] value, int weight) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CacheEntry<byte[], byte[]> putEntry(long currentTime, byte[] key,
            int keyHash, byte[] value, int weight) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CacheEntry<byte[], byte[]> putEntry(long currentTime,
            int timeToLiveSecs, byte[] key, int keyHash, byte[] value,
            int weight) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
    /**********************************************************************
    /* Get methods
    /**********************************************************************
     */
    
    @Override
    public CacheEntry<byte[], byte[]> findEntry(long currentTime, byte[] key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CacheEntry<byte[], byte[]> findEntry(long currentTime, byte[] key,
            int keyHash) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
    /**********************************************************************
    /* Removals
    /**********************************************************************
     */
    
    @Override
    public CacheEntry<byte[], byte[]> removeEntry(long currentTime, byte[] key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CacheEntry<byte[], byte[]> removeEntry(long currentTime, byte[] key,
            int keyHash) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeAll() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public int invalidateStale(long currentTimeMsecs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int invalidateStale(long currentTimeMsecs, int maxToInvalidate) {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
    /**********************************************************************
    /* Statistics methods
    /**********************************************************************
     */
    
    @Override
    public CacheStats getStats() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void clearStats() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void decayStats(double ratio) {
        // TODO Auto-generated method stub
        
    }

}
