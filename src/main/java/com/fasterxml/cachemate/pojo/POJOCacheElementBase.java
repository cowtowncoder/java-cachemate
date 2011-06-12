package com.fasterxml.cachemate.pojo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.cachemate.*;

/**
 * Base class for various POJO cache elements; components that
 * store actual Java objects as-is (without serialization to bytes).
 */
abstract class POJOCacheElementBase<K, V, E extends POJOCacheEntryBase<K,V,E>>
    implements CacheElement<K, V>
{
    /*
    /**********************************************************************
    /* Configuration, converters
    /**********************************************************************
     */

    protected final KeyConverter<K> _keyConverter;
    
    /*
    /**********************************************************************
    /* Configuration, size/time-to-live limits
    /**********************************************************************
     */

    /**
     * Maximum number of entries that can be stored in the cache
     */
    protected int _maxEntries;
    
    /**
     * Length of time entries will remain non-stale in cache after being inserted;
     * measure in units of 256 milliseconds (about quarter of a second). Unit chosen
     * to allow using ints for time calculation.
     */
    protected int _configTimeToLive;

    /*
    /**********************************************************************
    /* Configuration, other
    /**********************************************************************
     */
    
    /**
     * Setting that defines how many entries are to be checked for staleness
     * when finding entries; 0 means that no checks are made
     */
    protected int _configInvalidatePerGet = 1;

    /**
     * Setting that defines how many entries are to be checked for staleness
     * when adding an entry (even if no free space is needed); 0 means that no checks would be made
     */
    protected int _configInvalidatePerInsert = 2;

    /*
    /**********************************************************************
    /* Information on current contents
    /**********************************************************************
     */

    /**
     * Primary hash area for entries
     */
    protected E[] _entries;
    
    /**
     * Total current weight (approximate size) of all keys and entries in
     * the cache.
     */
    protected long _currentContentsWeight;

    /**
     * Total number of entries in the cache
     */
    protected int _currentEntries;

    /*
    /**********************************************************************
    /* LRU/expiration linkage
    /**********************************************************************
     */
    
    /**
     * Placeholder entry that represents the "old" end of
     * linkage; oldest and least-recently used entries (accessible
     * via entry links)
     */
    protected E _oldEntryHead;

    /**
     * Placeholder entry that represents the "new" end of
     * linkage; newest and most-recently used entries (accessible
     * via entry links)
     */
    protected E _newEntryHead;
 
    /*
    /**********************************************************************
    /* Statistics: access stats
    /**********************************************************************
     */
    
    /**
     * Number of times an entry has been found within cache
     */
    protected int _hitCount;
    
    /**
     * Number of times an entry has been found within cache
     */
    protected int _missCount;

    /**
     * Number of times entries have been inserted in the cache
     */
    protected int _insertCount;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected POJOCacheElementBase(KeyConverter<K> keyConverter,
            int maxEntries, long timeToLiveSecs,
            E[] entries)
    {
        _keyConverter = keyConverter;
        _maxEntries = maxEntries;
        _configTimeToLive = (int) ((1000L * timeToLiveSecs) >> 8);
        _entries = entries;
    }

    protected static int calcHashAreaSize(int maxEntries)
    {
        int hashAreaSize = 16;
        
        while (hashAreaSize < maxEntries) {
            hashAreaSize += hashAreaSize;
        }
        return hashAreaSize;
    }
    
    /*
    /**********************************************************************
    /* Public API, config access, resizing
    /**********************************************************************
     */

    /*
    // More to be added:
     */

    public int getConfigInvalidatePerGet() {
        return _configInvalidatePerGet;
    }

    public void setConfigInvalidatePerGet(int value) {
        _configInvalidatePerGet = value;
    }

    /*
    /**********************************************************************
    /* Public methods: put, find, remove
    /**********************************************************************
     */
    
    @Override
    public final E putEntry(long currentTime, K key, V value, int weight) {
        return putEntry(currentTime, key, _keyConverter.keyHash(key), value, weight);
    }
    
    @Override
    public final E putEntry(long currentTime, K key, int keyHash, V value, int weight)
    {
        E existingEntry = _removeByPrimary(currentTime, key, keyHash);
        // Either way, need to add the new entry next, as newest and MRU
        int index = _primaryHashIndex(keyHash);
        E newEntry = _createEntry(key, keyHash, value, _timeToTimestamp(currentTime), weight,
                _entries[index]);
        _entries[index] = newEntry;

        _linkNewEntry(currentTime, newEntry, weight);
        return existingEntry;
    }

    @Override
    public final E findEntry(long currentTime, K key) {
        return findEntry(currentTime, key, _keyConverter.keyHash(key));
    }

    @Override
    public final E findEntry(long currentTime, K key, int keyHash)
    {
        int index = _primaryHashIndex(keyHash);
        // First, locate the entry, but keep track of position within hash/collision chain:
        E prev = null;
        E entry = _entries[index];
        int expireTime = _latestStaleTimestamp(currentTime);

        while (entry != null) {
            if ((entry._keyHash == keyHash) && _keyConverter.keysEqual(key, entry.getKey())) {
                // And if match, verify it is not stale
                if (entry._insertionTime <= expireTime) { // if stale, remove
                    _removeEntry(entry, index, prev);
                    entry = null;
                } else { // if not stale, move as LRU
                    // Also: make this the LRU entry (note: _newEntryHead and _oldEntryHead are placeholders)
                    // first, unlink from previous chain
                    prev = entry._lessRecentEntry;
                    E next = entry._moreRecentEntry;
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
            entry = entry._primaryCollision;
        }

        // also: if aggressively cleaning up, remove stale entries
        int count = _configInvalidatePerGet;
        while (count > 0 && _invalidateOldestIfStale(expireTime)) {
            --count;
        }
        return entry;
    }
    
    @Override
    public final E removeEntry(long currentTime, K key) {
        return removeEntry(currentTime, key, _keyConverter.keyHash(key));
    }
    
    @Override
    public final E removeEntry(long currentTime, K key, int keyHash)
    {
        // first, basic removal
        E entry = _removeByPrimary(currentTime, key, keyHash);
        // also: if aggressively cleaning up, remove stale entries
        int count = _configInvalidatePerInsert;
        if (count > 0) {
            int expireTime = _latestStaleTimestamp(currentTime);
            do {
                if (!_invalidateOldestIfStale(expireTime)) {
                    break;
                }
            } while (--count > 0);
        }
        return entry;
    }
    
    
    @Override
    public void removeAll()
    {
        // Easy enough to drop all:
        _resetOldestAndNewest();
        Arrays.fill(_entries, null);
        _currentContentsWeight = 0L;
        _currentEntries = 0;
        // but do not clear stats necessarily
    }
    
    /*
    /**********************************************************************
    /* Public methods, invalidation
    /**********************************************************************
     */

    @Override
    public final int invalidateStale(long currentTimeMsecs) {
        return invalidateStale(currentTimeMsecs, Integer.MAX_VALUE);
    }
    
    @Override
    public final int invalidateStale(long currentTimeMsecs, int maxToInvalidate)
    {
        int count = 0;
        int earliestNonStale = _latestStaleTimestamp(currentTimeMsecs);
        
        while (count < maxToInvalidate && _invalidateOldestIfStale(earliestNonStale)) {
            ++count;
        }
        return count;
    }
    
    /*
    /**********************************************************************
    /* Public API, stats
    /**********************************************************************
     */

    @Override
    public final int size() {
        return _currentEntries;
    }

    /**
     * Returns crude estimated memory usage for entries cache contains, not
     * including overhead of cache itself (which is small); slightly
     * lower than what {@link #weight} would return.
     */
    @Override
    public final long contentsWeight() { return _currentContentsWeight; }

    public abstract long maxContentsWeight();
    
    /**
     * Returns crude estimated memory usage for the cache as whole, including
     * contents
     */
    @Override
    public abstract long weight();
    
    public CacheStats getStats() {
        return new CacheStats(_hitCount, _missCount, _insertCount,
                size(), weight(),
                _maxEntries, maxContentsWeight());
    }

    @Override
    public final void clearStats()
    {
        _hitCount = 0;
        _missCount = 0;
        _insertCount = 0;
    }

    /**
     * Method that can be used to decay existing statistics, to keep
     * smoothed-out average of hit/miss ratios over time.
     */
    @Override
    public final void decayStats(double ratio)
    {
        _hitCount = (int) (_hitCount * ratio);
        _missCount = (int) (_missCount * ratio);
        _insertCount = (int) (_insertCount * ratio);
    }
    
    /*
    /**********************************************************************
    /* Support for unit tests
    /**********************************************************************
     */
    
    protected E oldestEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        E oldest = _oldEntryHead.newerEntry();
        return (oldest != _newEntryHead) ? oldest : null;
    }

    protected E newestEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        E newest = _newEntryHead.olderEntry();
        return (newest != _oldEntryHead) ? newest : null;
    }

    protected E leastRecentEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        E leastRecent = _oldEntryHead.moreRecentEntry();
        return (leastRecent != _newEntryHead) ? leastRecent : null;
    }

    protected E mostRecentEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        E mostRecent = _newEntryHead.lessRecentEntry();
        return (mostRecent != _oldEntryHead) ? mostRecent : null;
    }

    protected List<K> keysFromOldestToNewest()
    {
        ArrayList<K> keys = new ArrayList<K>();
        for (E entry = _oldEntryHead.newerEntry(); entry != _newEntryHead; entry = entry.newerEntry()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    protected List<K> keysFromLeastToMostRecent()
    {
        ArrayList<K> keys = new ArrayList<K>();
        for (E entry = _oldEntryHead.moreRecentEntry(); entry != _newEntryHead; entry = entry.moreRecentEntry()) {
            keys.add(entry.getKey());
        }
        return keys;
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected final int _primaryHashIndex(int keyHash) {
        return keyHash & (_entries.length - 1);
    }
    
    protected final static int _timeToTimestamp(long currentTimeMsecs)
    {
        int time = (int) (currentTimeMsecs >> 8); // divide by 256, to quarter-seconds
        if (time < 0) {
            time &= 0x7FFFFFFF;
        }
        return time;
    }
    
    /**
     * Helper method that will return internal timestamp value (in units of
     * 256 milliseconds, i.e. quarter second) of the latest timepoint
     * that is stale.
     */
    protected int _latestStaleTimestamp(long currentTimeMsecs)
    {
        // First, convert current time to units of ~1/4 second
        int currentTime = _timeToTimestamp(currentTimeMsecs);
        // then go back by time-to-live time units:
        currentTime -= _configTimeToLive;
        return currentTime;
    }
    
    /**
     * Method that will delete the oldest entry in the cache, if there
     * is at least one entry in the cache, and it was inserted at or
     * before given timepoint.
     * 
     * @param latestStaleTime Timestamp that indicates maximum timestamp that is considered
     *    stale (in units of ~1/4 seconds)
     */
    protected boolean _invalidateOldestIfStale(int latestStaleTime)
    {
        E oldest = _oldEntryHead._newerEntry;
        if (oldest != _newEntryHead) {
            /* ok now: timestamps we use are relative (due to truncation), so
             * we MUST compare by subtraction, compare difference
             */
            int diff = oldest._insertionTime - latestStaleTime;
            if (diff <= 0) { // created at or before expiration time (latest timestamp that is stale)
                _removeEntry(oldest);
                return true;
            }
        }
        return false;
    }

    protected boolean _invalidateOldest()
    {
        E oldest = _oldEntryHead._newerEntry;
        if (oldest != _newEntryHead) {
            _removeEntry(oldest);
            return true;
        }
        return false;
    }

    protected final E _removeByPrimary(long currentTime, K key, int keyHash)
    {
        int index = (keyHash & (_entries.length - 1));
        // First, locate the entry
        E prev = null;
        E entry = _entries[index];
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
    
    protected abstract void _removeEntry(E entry);

    protected abstract void _removeEntry(E entry, int hashIndex, E prevInHash);
    
    protected final void _resetOldestAndNewest()
    {
        _newEntryHead = _createDummyEntry();
        _oldEntryHead = _createDummyEntry();
        _newEntryHead._olderEntry = _oldEntryHead;
        _newEntryHead._lessRecentEntry = _oldEntryHead;
        _oldEntryHead._newerEntry = _newEntryHead;
        _oldEntryHead._moreRecentEntry = _newEntryHead;
    }

    protected abstract E _createDummyEntry();

    protected abstract E _createEntry(K key, int keyHash, V value, int timestamp, int weight, E nextCollision);

    protected final void _linkNewEntry(long currentTime, E newEntry, int weight)
    {
        // ok; first insertion-order linked list:
        E next = _newEntryHead;
        E prev = next._olderEntry;
        next._olderEntry = newEntry;
        newEntry._newerEntry = next;
        prev._newerEntry = newEntry;
        newEntry._olderEntry = prev;
        // then LRU listing (insertion counts as access, hence new entry will be most-recently-used)
        prev = next._lessRecentEntry;
        next._lessRecentEntry = newEntry;
        newEntry._moreRecentEntry = next;
        prev._moreRecentEntry = newEntry;
        newEntry._lessRecentEntry = prev;
        // then update stats
        _currentEntries++;
        _currentContentsWeight += weight;

        // Ok, then; let's see if we need to remove stale entries
        int count = _configInvalidatePerInsert;
        int expireTime = _latestStaleTimestamp(currentTime);
        final long maxContentsWeight = maxContentsWeight();
        while ((count > 0) || (_currentEntries > _maxEntries) || (_currentContentsWeight > maxContentsWeight)) {
            if (!_invalidateOldestIfStale(expireTime)) {
                break;
            }
            --count;
        }
        // And if we are still above limit, remove LRU entries
        count = 0;
        while ((_currentEntries > _maxEntries) || (_currentContentsWeight > maxContentsWeight)) {
            E lru = _oldEntryHead._moreRecentEntry;
            if (lru == _newEntryHead) { // should never occur...
                throw new IllegalStateException("Flushed "+count+" entries, cache empty, still too many entries ("+_currentEntries
                        +") or too much weight ("+_currentContentsWeight+")");
            }
            _removeEntry(lru);
            ++count;
        }
    }
}

