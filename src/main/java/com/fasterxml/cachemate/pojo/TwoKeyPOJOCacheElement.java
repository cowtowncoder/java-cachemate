package com.fasterxml.cachemate.pojo;

import java.util.Arrays;

import com.fasterxml.cachemate.*;
import com.fasterxml.cachemate.converters.KeyConverter;
import com.fasterxml.cachemate.util.PlatformConstants;
import com.fasterxml.cachemate.util.TimeUtil;

public class TwoKeyPOJOCacheElement<K1,K2,V>
    extends POJOCacheElementBase<K1,V,TwoKeyPOJOCacheEntry<K1,K2,V>>
    implements TwoKeyCacheElement<K1,K2,V>
{
    /**
     * We have about this many fields; just used for estimating rough in-memory size
     * for the cache as total.
     */
    protected final static int IMPL_FIELD_COUNT = BASE_FIELD_COUNT + 3;

    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (IMPL_FIELD_COUNT * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

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
            int timeToLiveSecs)
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

    @Override
    public final TwoKeyPOJOCacheEntry<K1, K2, V> putEntry(long currentTime,
            K1 primaryKey, K2 secondaryKey, V value, int weight)
    {
        int primHash = _keyConverter.keyHash(primaryKey);
        int secHash = (secondaryKey == null) ? 0 : _secondaryKeyConverter.keyHash(secondaryKey);
        return _putEntry(currentTime, _configTimeToLive, primaryKey, primHash,
                secondaryKey, secHash, value, weight);
    }

    @Override
    public final TwoKeyPOJOCacheEntry<K1, K2, V> putEntry(long currentTime, int timeToLiveSecs,
            K1 primaryKey, K2 secondaryKey, V value, int weight)
    {
        int primHash = _keyConverter.keyHash(primaryKey);
        int secHash = (secondaryKey == null) ? 0 : _secondaryKeyConverter.keyHash(secondaryKey);
        return _putEntry(currentTime, TimeUtil.secondsToInternal(timeToLiveSecs), primaryKey, primHash,
                secondaryKey, secHash, value, weight);
    }
    
    @Override
    public final TwoKeyPOJOCacheEntry<K1, K2, V> putEntry(long currentTime,
            K1 primaryKey, int primaryKeyHash, K2 secondaryKey, int secondaryKeyHash,
            V value, int weight)
    {
        return _putEntry(currentTime, _configTimeToLive, primaryKey, primaryKeyHash,
                secondaryKey, secondaryKeyHash, value, weight);
    }

    @Override
    public final TwoKeyPOJOCacheEntry<K1, K2, V> putEntry(long currentTime, int timeToLiveSecs,
            K1 primaryKey, int primaryKeyHash, K2 secondaryKey, int secondaryKeyHash,
            V value, int weight)
    {
        return _putEntry(currentTime, TimeUtil.secondsToInternal(timeToLiveSecs), primaryKey, primaryKeyHash,
                secondaryKey, secondaryKeyHash, value, weight);
    }

    /**
     * Actual method used by all convenience methods for putting given entry in
     * cache.
     */
    protected TwoKeyPOJOCacheEntry<K1, K2, V> _putEntry(long currentTime, int timeToLiveQ,
            K1 primaryKey, int primaryKeyHash, K2 secondaryKey, int secondaryKeyHash,
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
            nextSecondary = _secondaryEntries[secondaryIndex];        
        }
        TwoKeyPOJOCacheEntry<K1, K2, V> newEntry = new TwoKeyPOJOCacheEntry<K1, K2, V>(
                primaryKey, primaryKeyHash, secondaryKey, secondaryKeyHash,
                value, TimeUtil.timeToTimestamp(currentTime) + timeToLiveQ, weight,
                nextPrimary, nextSecondary);
        _entries[primaryIndex] = newEntry;
        if (secondaryKey != null) {
            _secondaryEntries[secondaryIndex] = newEntry;
        }
        _linkNewEntry(currentTime, newEntry, weight);
        return existingEntry;
    }
    
    @Override
    public TwoKeyPOJOCacheEntry<K1, K2, V> findEntryBySecondary(long currentTime, K2 secondaryKey)
    {
        if (secondaryKey == null) {
            return null;
        }
        return findEntryBySecondary(currentTime, secondaryKey,
            _secondaryKeyConverter.keyHash(secondaryKey));
    }

    @Override
    public TwoKeyPOJOCacheEntry<K1, K2, V> findEntryBySecondary(long currentTime, K2 secondaryKey, int secondaryHash)
    {
        if (secondaryKey == null) {
            return null;
        }
        int index = _secondaryHashIndex(secondaryHash);
        // First, locate the entry, but keep track of position within hash/collision chain:
        TwoKeyPOJOCacheEntry<K1, K2, V> prev = null;
        TwoKeyPOJOCacheEntry<K1, K2, V> entry = _secondaryEntries[index];
        int currTimeInQ = TimeUtil.timeToTimestamp(currentTime);

        while (entry != null) {
            if ((entry._keyHash2 == secondaryHash) && _secondaryKeyConverter.keysEqual(secondaryKey, entry.getSecondaryKey())) {
                // And if match, verify it is not stale
                if (_expired(entry, currTimeInQ)) {
                    _removeEntry(entry);
                    entry = null;
                } else { // if not stale, move as LRU
                    // Also: make this the LRU entry (note: _newEntryHead and _oldEntryHead are placeholders)
                    // first, unlink from previous chain
                    prev = entry._lessRecentEntry;
                    TwoKeyPOJOCacheEntry<K1, K2, V> next = entry._moreRecentEntry;
                    prev._moreRecentEntry = next;
                    next._lessRecentEntry = prev;
                    // then add as new head (wrt LRU)
                    next = _newEntryHead;
                    prev = _newEntryHead._lessRecentEntry;
                    prev._moreRecentEntry = entry;
                    entry._lessRecentEntry = prev;
                    next._lessRecentEntry = entry;
                    entry._moreRecentEntry = next;

                    // and finally, update match count; may be used to decide on promotion/demotion
                    ++entry._timesReturned;
                }
                break;
            }
            prev = entry;
            entry = entry._secondaryCollision;
        }

        // also: if aggressively cleaning up, remove stale entries
        int count = _configInvalidatePerGet;
        while (count > 0 && _invalidateOldestIfStale(currTimeInQ)) {
            --count;
        }
        return entry;
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
    protected final TwoKeyPOJOCacheEntry<K1,K2,V> _removeByPrimary(long currentTime, K1 key, int keyHash)
    {
        int index = (keyHash & (_entries.length - 1));
        // First, locate the entry
        TwoKeyPOJOCacheEntry<K1,K2,V> prev = null;
        TwoKeyPOJOCacheEntry<K1,K2,V> entry = _entries[index];
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

    /* Since may have secondary key, this method needs bit more work compared
     * to the base class variant
     */
    @Override
    protected void _removeEntry(TwoKeyPOJOCacheEntry<K1,K2,V> entry, int primaryHashIndex,
            TwoKeyPOJOCacheEntry<K1,K2,V> prevInPrimary)
    {
        // let's also see if there's secondary key to remove:
        int secondaryHashIndex;
        TwoKeyPOJOCacheEntry<K1,K2,V> prevSecondary = null;
        if (entry.hasSecondaryKey()) {
            secondaryHashIndex = _secondaryHashIndex(entry._keyHash2);
            TwoKeyPOJOCacheEntry<K1,K2,V> currSecondary = _secondaryEntries[secondaryHashIndex];
            while (true) {
                if (currSecondary == null) {
                    // should never occur, so:
                    throw new IllegalStateException("Internal data error: could not find entry with secondary key "
                            +entry.getSecondaryKey()+", index "+secondaryHashIndex+"/"+_entries.length);
                }
                if (currSecondary == entry) {
                    break;
                }
                prevSecondary = currSecondary;
                currSecondary = currSecondary._secondaryCollision;
            }
        } else {
            secondaryHashIndex = -1;
        }
        _removeEntry(entry, primaryHashIndex, prevInPrimary, secondaryHashIndex, prevSecondary);
    }
    
    @Override
    protected void _removeEntry(TwoKeyPOJOCacheEntry<K1,K2,V> entry)
    {
        // Ok, need to locate entry in primary hash first
        int primaryIndex = _primaryHashIndex(entry._keyHash);
        TwoKeyPOJOCacheEntry<K1,K2,V> curr = _entries[primaryIndex];
        TwoKeyPOJOCacheEntry<K1,K2,V> prevPrimary = null;

        while (true) {
            if (curr == null) {
                // should never occur, so:
                throw new IllegalStateException("Internal data error: could not find entry (index "+primaryIndex+"/"+_entries.length+"), with primary key "+entry.getKey());
            }
            if (curr == entry) {
                break;
            }
            prevPrimary = curr;
            curr = curr._primaryCollision;
        }
        // and possibly also need to remove from secondary hash table:
        TwoKeyPOJOCacheEntry<K1,K2,V> prevSecondary = null;
        int secondaryIndex;

        if (entry.hasSecondaryKey()) {
            secondaryIndex = _secondaryHashIndex(entry._keyHash2);
            curr = _secondaryEntries[secondaryIndex];
            while (true) {
                if (curr == null) {
                    // should never occur, so:
                    throw new IllegalStateException("Internal data error: could not find entry (index "+secondaryIndex+"/"+_entries.length
                            +"), with secondary key "+entry.getSecondaryKey());
                }
                if (curr == entry) {
                    break;
                }
                prevSecondary = curr;
                curr = curr._secondaryCollision;
            }
        } else {
            secondaryIndex = -1;
        }
        _removeEntry(entry, primaryIndex, prevPrimary, secondaryIndex, prevSecondary);
        return;
    }
    
    protected void _removeEntry(TwoKeyPOJOCacheEntry<K1,K2,V> entry,
            int primaryHashIndex, TwoKeyPOJOCacheEntry<K1,K2,V> prevInPrimary,
            int secondaryHashIndex, TwoKeyPOJOCacheEntry<K1,K2,V> prevInSecondary)
    {
        // First, update counts
        --_currentEntries;
        _currentContentsWeight -= entry._weight;

        // Unlink from hash area; first primary
        TwoKeyPOJOCacheEntry<K1,K2,V> next = entry._primaryCollision;
        if (prevInPrimary == null) {
            _entries[primaryHashIndex] = next;
        } else {
            prevInPrimary._primaryCollision = next;
        }

        // then secondary
        if (secondaryHashIndex >= 0) {
            next = entry._secondaryCollision;
            if (prevInSecondary == null) {
                _secondaryEntries[secondaryHashIndex] = next;
            } else {
                prevInSecondary._secondaryCollision = next;
            }
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
    protected TwoKeyPOJOCacheEntry<K1,K2,V> _createEntry(K1 key, int keyHash, V value,
            int expirationTime, int weight,
            TwoKeyPOJOCacheEntry<K1,K2,V> nextCollision) {
        return new TwoKeyPOJOCacheEntry<K1,K2,V>(key, keyHash, null, 0, value, expirationTime, weight,
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
        super.checkSanity();
        int expCount = _currentEntries;

        // check secondary chain too
        int secondaryCount = 0;
        for (TwoKeyPOJOCacheEntry<K1,K2,V> entry : _secondaryEntries) {
            while (entry != null) {
                ++secondaryCount;
                entry = entry._secondaryCollision;
            }
        }
        if (secondaryCount != expCount) {
            throw new IllegalStateException("Invalid count by secondary: actual "+secondaryCount+"; expected "+expCount);
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
