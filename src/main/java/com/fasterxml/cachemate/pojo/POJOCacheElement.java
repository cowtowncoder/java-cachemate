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
     * This base class has this many fields; count is
     * used for estimating rough in-memory size
     * for the cache as total.
     */
    protected final static int IMPL_FIELD_COUNT = BASE_FIELD_COUNT + 1;

    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (IMPL_FIELD_COUNT * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

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
    /* Overridden/implemented base class methods
    /**********************************************************************
     */

    @Override
    protected POJOCacheEntry<K,V> _createDummyEntry() {
        return new POJOCacheEntry<K,V>();
    }

    @Override
    protected POJOCacheEntry<K,V> _createEntry(K key, int keyHash, V value, int timestamp, int weight,
            POJOCacheEntry<K,V> nextCollision) {
        return new POJOCacheEntry<K,V>(key, keyHash, value, timestamp, weight, nextCollision);
    }
    
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

    protected final POJOCacheEntry<K,V> _removeByPrimary(long currentTime, K key, int keyHash)
    {
        int index = (keyHash & (_entries.length - 1));
        // First, locate the entry
        POJOCacheEntry<K,V> prev = null;
        POJOCacheEntry<K,V> entry = _entries[index];
        if (entry != null) {
            while (entry != null) {
                if ((entry._keyHash == keyHash) && _keyConverter.keysEqual(key, entry.getKey())) {
                    _removeEntry(entry, index, prev);
                    return entry;
                }
                prev = entry;
                entry = entry._primaryCollision;
            }
        }
        return null;
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
