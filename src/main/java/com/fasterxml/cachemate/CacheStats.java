package com.fasterxml.cachemate;

/**
 * Simple container class for statistics regarding a single
 * {@link CacheElement}
 */
public class CacheStats
{
    protected final int _hits;
    protected final int _misses;
    protected final int _insertions;

    protected final int _entryCount;
    protected final long _contentsWeight;
    protected final long _totalWeight;

    protected final int _maxEntryCount;
    protected final long _maxTotalWeight;
    
    public CacheStats(int hits, int misses, int insertions,
            int entryCount, long contentsWeight, long totalWeight,
            int maxEntryCount, long maxTotalWeight)
    {
        _hits = hits;
        _misses = misses;
        _insertions = insertions;
        _entryCount = entryCount;
        _contentsWeight = contentsWeight;
        _totalWeight = totalWeight;
        _maxEntryCount = maxEntryCount;
        _maxTotalWeight = maxTotalWeight;
    }

    public int getHits() { return _hits; }
    public int getMisses() { return _misses; }
    public int getTotalGets() { return _hits + _misses; }

    public int getEntryCount() { return _entryCount; }
    public long getContentsWeight() { return _contentsWeight; }
    public long getTotalWeight() { return _totalWeight; }

    public int getMaxEntryCount() { return _maxEntryCount; }
    public long getMaxTotalWeight() { return _maxTotalWeight; }

}