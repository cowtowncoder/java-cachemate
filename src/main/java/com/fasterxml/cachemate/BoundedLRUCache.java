package com.fasterxml.cachemate;

import java.util.Arrays;

public class BoundedLRUCache<K, V>
{
    // // // Configuration

    protected final KeyConverter _keyConverter;
    
    /**
     * Maximum number of entries cache can have
     */
    protected long _maxWeight;

    /**
     * Maximum number of entries that can be stored in the cache
     */
    protected int _maxEntries;
    
    // // // Current load

    protected long _currentWeight;

    protected int _currentEntries;
    
    // // // Actual entries

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

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    @SuppressWarnings("unchecked")
    public BoundedLRUCache(KeyConverter<K> keyConverter,
            int maxEntries, long maxWeight)
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
    }
    
    /*
    /**********************************************************************
    /* Public API, basic access
    /**********************************************************************
     */

    public V findEntry(K key)
    {
        // !!! TBI
        return null;
    }

    public V remoteEntry(K key)
    {
        // !!! TBI
        return null;
    }

    /**
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     */
    public void putEntry(K key, V value, int weight)
    {
        // !!! TBI
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

    public Stats getStats() {
        return new Stats(_hitCount, _missCount);
    }

    public void clearStats() {
        _hitCount = 0;
        _missCount = 0;
    }

    /**
     * Method that can be used to decay existing statistics, to keep
     * smoothed-out average of hit/miss ratios over time.
     */
    public void decayStats(double ratio)
    {
        _hitCount = (int) (_hitCount * ratio);
        _missCount = (int) (_missCount * ratio);
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    
    /*
    /**********************************************************************
    /* Helper classes
    /**********************************************************************
     */

    public final static class Stats
    {
        protected final int _hits, _misses;
        
        public Stats(int hits, int misses)
        {
            _hits = hits;
            _misses = misses;
        }

        public int getHits() { return _hits; }
        public int getMisses() { return _misses; }
        public int getTotal() { return _hits + _misses; }
    }
    
    /**
     * Entry for cache; contains both key and value, to reduce number
     * of object instances needed.
     */
    protected final static class Entry<K, V>
    {
        public final K _key;
        
        public final V _value;

        /**
         * Hash code of the key value (since it is possible
         * that hashCode of key itself is not useful, as is the case
         * for byte[])
         */
        public final int _keyHash;
        
        public final Entry<K, V> _nextNewer;
        
        public final Entry<K, V> _nextCollision;
        
        /**
         * Weight of this entry, including both key and value
         */
        public final int _weight;
        
        public Entry(K key, int keyHash, V value, int weight,
                Entry<K,V> nextNewer, Entry<K,V> nextCollision)
        {
            _key = key;
            _value = value;
            _keyHash = keyHash;
            _weight = weight;
            _nextNewer = nextNewer;
            _nextCollision = nextCollision;
        }
    }
}
