package com.fasterxml.cachemate.pojo;

import com.fasterxml.cachemate.KeyConverter;
import com.fasterxml.cachemate.PlatformConstants;
import com.fasterxml.cachemate.CacheElement;

/**
 * Special data structure used as an element of a cache (or in simplest cases,
 * as simple single-level in-memory object cachje). Evictions are based both on
 * staleness/insertion-time limit and capacity limitations (with LRU eviction).
 * Keys and values are POJOs (as opposed to being serialized byte sequences);
 * and only single _key is used.
 *<p>
 * Note on implementation: hash area is allocated on construction based on specified
 * maximum number of entries (allocate chunk with size that is next biggest power of two),
 * and remains static in size unless explicit resizing is requested.
 * Because of this, it makes sense to use sensible maximum entry count, as well as
 * maximum weight (rough memory usage estimation)
 * 
 * @author Tatu Saloranta
 *
 * @param <K> Type of keys cache element contains
 * @param <V> Type of values cache element containts
 */
public class POJOCacheElement<K, V>
    extends POJOCacheElementBase<K, V, POJOCacheEntry<K, V>>
    implements CacheElement<K, V>
{
    /**
     * We have about this many fields; just used for estimating rough in-memory size
     * for the cache as total.
     */
    private final static int FIELD_COUNT = 14;
    
    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (FIELD_COUNT * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

    // // // Actual entries

    /**
     * Maximum weight (approximate size) of all entries cache can contain.
     * Set to maximum weight allowed minus overhead of the cache structure
     * itself.
     */
    protected long _maxContentsWeight;
    
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
    public POJOCacheElement(KeyConverter<K> keyConverter,
            int maxEntries, long maxWeight,
            long timeToLiveSecs)
    {
        super(keyConverter, maxEntries, timeToLiveSecs,
                (POJOCacheEntry<K,V>[]) new POJOCacheEntry<?,?>[calcHashAreaSize(maxEntries)]);
        _resetOldestAndNewest(); // to set oldest/newest (head/tail) linked
        // take into account base mem usage of the cache (crude, but...), including hash area
        _maxContentsWeight = maxWeight - BASE_MEM_USAGE - (_entries.length * PlatformConstants.BASE_FIELD_MEMORY_USAGE);
    }
    
    /*
    /**********************************************************************
    /* Public methods, other
    /**********************************************************************
     */

    @Override
    public final long weight() {
        return BASE_MEM_USAGE + _currentContentsWeight
            + (_entries.length * PlatformConstants.BASE_FIELD_MEMORY_USAGE);
    }

    @Override
    public long maxContentsWeight() {
        return _maxContentsWeight;
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    @Override
    protected void _removeEntry(POJOCacheEntry<K,V> entry)
    {
        // Ok, need to locate entry in hash...
        int index = _primaryHashIndex(entry._keyHash);
        POJOCacheEntry<K,V> curr = _entries[index];
        POJOCacheEntry<K,V> prev = null;
        while (curr != null) {
            if (curr == entry) {
                _removeEntry(entry, index, prev);
                return;
            }
            prev = curr;
            curr = curr._primaryCollision;
        }
        // should never occur, so:
        throw new IllegalStateException("Internal data error: could not find entry (index "+index+"/"+_entries.length+"), _key "+entry.getKey());
    }

    @Override
    protected void _removeEntry(POJOCacheEntry<K,V> entry, int hashIndex, POJOCacheEntry<K,V> prevInHash)
    {
        // First, update counts
        --_currentEntries;
        _currentContentsWeight -= entry._weight;

        // Unlink from hash area
        POJOCacheEntry<K,V> next = entry._primaryCollision;
        if (prevInHash == null) {
            _entries[hashIndex] = next;
        } else {
            prevInHash._primaryCollision = next;
        }
        // and from linked lists:
        entry.unlink();

//checkSanity();
    }

    @Override
    protected POJOCacheEntry<K,V> _createDummyEntry() {
        return new POJOCacheEntry<K,V>();
    }

    @Override
    protected POJOCacheEntry<K,V> _createEntry(K key, int keyHash, V value, int timestamp, int weight,
            POJOCacheEntry<K,V> nextCollision) {
        return new POJOCacheEntry<K,V>(key, keyHash, value, timestamp, weight, nextCollision);
    }
    
    /*
    /**********************************************************************
    /* Diagnostic methods
    /**********************************************************************
     */
    
    /**
     * Method that tries to cross-check statistics to ensure that they are
     * correct and compatible with each other; and if not, throw an
     * {@link IllegalArgumentException} with details.
     * Note that this should never need to be used for normal use; but
     * may be called in case there are suspicions that the internal state
     * could be corrupt due to synchronization issues (incorrect multi-threaded
     * use of instance without proper synchronization)
     */
    protected void checkSanity()
    {
        int actualCount = 0;

        // First: calculate real entry count from hash table and spill-over links
        for (POJOCacheEntry<K,V> entry : _entries) {
            while (entry != null) {
                ++actualCount;
                entry = entry._primaryCollision;
            }
        }
        // and compare it to assumed number of entries
        if (actualCount != _currentEntries) {
            throw new IllegalStateException("Invalid count: actual "+actualCount+"; expected "+_currentEntries);
        }
    }
}
