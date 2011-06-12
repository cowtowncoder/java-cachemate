package com.fasterxml.cachemate.pojo;

import java.util.Arrays;

import com.fasterxml.cachemate.*;

public class TwoKeyPOJOCacheElement<K1,K2,V>
    extends POJOCacheElementBase<K1,V,TwoKeyPOJOCacheEntry<K1,K2,V>>
    implements TwoKeyCacheElement<K1,K2,V>
{
    /**
     * We have about this many fields; just used for estimating rough in-memory size
     * for the cache as total.
     */
    private final static int FIELD_COUNT = 14;
    
    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (FIELD_COUNT * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

    /*
    /**********************************************************************
    /* Configuration, converters
    /**********************************************************************
     */

    protected final KeyConverter<K2> _secondaryKeyConverter;
    
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

    /*
    /**********************************************************************
    /* Hash area for secondary name lookup
    /**********************************************************************
     */
    
    /**
     * Secondary hash area for entries, for primary key lookups
     */
    protected TwoKeyPOJOCacheEntry<K1,K2,V>[] _secondaryEntries;
    
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
    public TwoKeyPOJOCacheElement(KeyConverter<K1> keyConverter, KeyConverter<K2> secondaryKeyConverter,
            int maxEntries, long maxWeight,
            long timeToLiveSecs)
    {
        super(keyConverter, maxEntries, timeToLiveSecs,
            (TwoKeyPOJOCacheEntry<K1,K2,V>[]) new TwoKeyPOJOCacheEntry<?,?,?>[calcHashAreaSize(maxEntries)]);
        _secondaryKeyConverter = secondaryKeyConverter;
        _resetOldestAndNewest(); // to set oldest/newest (head/tail) linked
        _secondaryEntries = (TwoKeyPOJOCacheEntry<K1,K2,V>[]) new TwoKeyPOJOCacheEntry<?,?,?>[_entries.length];
        // take into account base mem usage of the cache (crude, but...), including hash area
        _maxContentsWeight = maxWeight - BASE_MEM_USAGE
            - (_entries.length * PlatformConstants.BASE_FIELD_MEMORY_USAGE)
            - (_secondaryEntries.length * PlatformConstants.BASE_FIELD_MEMORY_USAGE)
            ;
    }
    
    /*
    /**********************************************************************
    /* Public API: put, find, remove (using primary)
    /**********************************************************************
     */

    @Override
    public void removeAll()
    {
        super.removeAll();
        Arrays.fill(_secondaryEntries, null);
    }

    /*
    /**********************************************************************
    /* Put/find/remove methods that use secondary key
    /**********************************************************************
     */

    public TwoKeyPOJOCacheEntry<K1, K2, V> putEntry(long currentTime, K1 primaryKey, K2 secondaryKey, V value, int weight)
    {
        int primHash = _keyConverter.keyHash(primaryKey);
        int secHash = (secondaryKey == null) ? 0 : _secondaryKeyConverter.keyHash(secondaryKey);
        return putEntry(currentTime, primaryKey, primHash, secondaryKey, secHash, value, weight);
    }

    public TwoKeyPOJOCacheEntry<K1, K2, V> putEntry(long currentTime, K1 primaryKey, int primaryKeyHash,
            K2 secondaryKey, int secondaryKeyHash,
            V value, int weight)
    {
        TwoKeyPOJOCacheEntry<K1, K2, V> existingEntry = _removeByPrimary(currentTime,
                primaryKey, primaryKeyHash);
        // Either way, need to add the new entry next, as newest and MRU
        int primaryIndex = _primaryHashIndex(primaryKeyHash);
        TwoKeyPOJOCacheEntry<K1, K2, V> nextPrimary = _entries[primaryIndex];        
        TwoKeyPOJOCacheEntry<K1, K2, V> nextSecondary;
        int secondaryIndex;
        // secondary key is optional, so:
        if (secondaryKey == null) {
            secondaryIndex = -1;
            nextSecondary = null;
        } else {
            secondaryIndex = _secondaryHashIndex(secondaryKeyHash);
            nextSecondary = _entries[primaryIndex];        
        }
        TwoKeyPOJOCacheEntry<K1, K2, V> newEntry = new TwoKeyPOJOCacheEntry<K1, K2, V>(
                primaryKey, primaryKeyHash, secondaryKey, secondaryKeyHash,
                value, _timeToTimestamp(currentTime), weight,
                nextPrimary, nextSecondary);
        _entries[primaryIndex] = newEntry;
        if (secondaryKey != null) {
            _secondaryEntries[secondaryIndex] = newEntry;
        }
        _linkNewEntry(currentTime, newEntry, weight);
        return existingEntry;
    }
    
    public TwoKeyPOJOCacheEntry<K1, K2, V> findEntryBySecondary(long currentTime, K2 secondaryKey)
    {
        return null;
    }
    public TwoKeyPOJOCacheEntry<K1, K2, V> findEntryBySecondary(long currentTime, K2 secondaryKey, int keyHash)
    {
        return null;
    }

    public TwoKeyPOJOCacheEntry<K1, K2, V> removeEntryBySecondary(long currentTime, K2 secondaryKey)
    {
        return null;
    }
    public TwoKeyPOJOCacheEntry<K1, K2, V> removeEntryBySecondary(long currentTime, K2 secondaryKey, int secondaryKeyHash)
    {
        return null;
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
    protected void _removeEntry(TwoKeyPOJOCacheEntry<K1,K2,V> entry)
    {
        // Ok, need to locate entry in hash...
        int index = _primaryHashIndex(entry._keyHash);
        TwoKeyPOJOCacheEntry<K1,K2,V> curr = _entries[index];
        TwoKeyPOJOCacheEntry<K1,K2,V> prev = null;
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
    
    protected void _removeEntry(TwoKeyPOJOCacheEntry<K1,K2,V> entry, int hashIndex, TwoKeyPOJOCacheEntry<K1,K2,V> prevInHash)
    {
        // First, update counts
        --_currentEntries;
        _currentContentsWeight -= entry._weight;

        // Unlink from hash area
        TwoKeyPOJOCacheEntry<K1,K2,V> next = entry._primaryCollision;
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
    protected TwoKeyPOJOCacheEntry<K1,K2,V> _createDummyEntry() {
        return new TwoKeyPOJOCacheEntry<K1,K2,V>();
    }

    /**
     * This method can be called too, although it will then assume that no
     * secondary key is used.
     */
    @Override
    protected TwoKeyPOJOCacheEntry<K1,K2,V> _createEntry(K1 key, int keyHash, V value, int timestamp, int weight,
            TwoKeyPOJOCacheEntry<K1,K2,V> nextCollision) {
        return new TwoKeyPOJOCacheEntry<K1,K2,V>(key, keyHash, null, 0, value, timestamp, weight,
                nextCollision, null);
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
        for (TwoKeyPOJOCacheEntry<K1,K2,V> entry : _entries) {
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

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    
    protected final int _secondaryHashIndex(int secondaryKeyHash) {
        return secondaryKeyHash & (_secondaryEntries.length - 1);
    }

}
