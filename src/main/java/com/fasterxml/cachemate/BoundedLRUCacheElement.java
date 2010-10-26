package com.fasterxml.cachemate;

import java.util.*;

/**
 * Special data structure used as an element of a cache (or in simplest cases,
 * as simple single-level in-memory object cachje). Evictions are based both on
 * staleness/insertion-time limit and capacity limitations (with LRU eviction).
 *<p>
 * Note on implementation: hash area is allocated on construction based on specified
 * maximum number of entries (allocate chunk with size that is next biggest power of two),
 * and remains static in size unless explicit resizing is requested.
 * Because of this, it makes sense to use sensible maximum entry count, as well as
 * maximum weight (rough memory usage estimation)
 * 
 * @author tatu
 *
 * @param <K>
 * @param <V>
 */
public class BoundedLRUCacheElement<K, V>
{
    /**
     * We have about this many fields; just used for estimating rough in-memory size
     * for the cache as total.
     */
    private final static int FIELD_COUNT = 14;
    
    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (FIELD_COUNT * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

    // // // Configuration

    protected final KeyConverter<K> _keyConverter;
    
    /**
     * Maximum weight (approximate size) of all entries cache can contain.
     * Set to maximum weight allowed minus overhead of the cache structure
     * itself.
     */
    protected long _maxContentsWeight;

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
    
    // // // Current load

    /**
     * Total current weight (approximate size) of all keys and entries in
     * the cache.
     */
    protected long _currentContentsWeight;

    /**
     * Total number of entries in the cache
     */
    protected int _currentEntries;
    
    // // // Actual entries

    /**
     * Primary hash area for entries
     */
    protected CacheEntry<K, V>[] _entries;
    
    /**
     * Placeholder entry that represents the "old" end of
     * linkage; oldest and least-recently used entries (accessible
     * via entry links)
     */
    protected CacheEntry<K, V> _oldEntryHead;

    /**
     * Placeholder entry that represents the "new" end of
     * linkage; newest and most-recently used entries (accessible
     * via entry links)
     */
    protected CacheEntry<K, V> _newEntryHead;
    
    // // // Statistics
    
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
    /* Construction
    /**********************************************************************
     */

    /**
     * @param timeToLiveSecs Amount of time entries will remain fresh (non-stale) in
     *   cache; in seconds.
     */
    @SuppressWarnings("unchecked")
    public BoundedLRUCacheElement(KeyConverter<K> keyConverter,
            int maxEntries, long maxWeight,
            long timeToLiveSecs)
    {
        _keyConverter = keyConverter;
        _maxEntries = maxEntries;
        _resetOldestAndNewest(); // to set oldest/newest (head/tail) linked
        // array needs to be a power of two, just find next bigger:
        int hashAreaSize = 16;
        
        while (hashAreaSize < maxEntries) {
            hashAreaSize += hashAreaSize;
        }
        _entries = (CacheEntry<K,V>[]) new CacheEntry<?,?>[hashAreaSize];

        // take into account base mem usage of the cache (crude, but...), including hash area
        _maxContentsWeight = maxWeight - BASE_MEM_USAGE - (hashAreaSize * PlatformConstants.BASE_FIELD_MEMORY_USAGE);
        _configTimeToLive = (int) ((1000L * timeToLiveSecs) >> 8);
    }
    
    /*
    /**********************************************************************
    /* Public API, config access, resizing
    /**********************************************************************
     */
    
    // To be implemented!!!
    
    /*
    /**********************************************************************
    /* Public API, basic access
    /**********************************************************************
     */

    /**
     * Method for finding entry with specified key from this cache element;
     * returns null if no such entry exists; otherwise found entry
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     */
    public CacheEntry<K,V> findEntry(long currentTime, K key)
    {
        return findEntry(currentTime, key, _keyConverter.keyHash(key));
    }
    
    /**
     * Method for finding entry with specified key from this cache element;
     * returns null if no such entry exists; otherwise found entry
     * 
     * @param currentTime Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     */
    public CacheEntry<K,V> findEntry(long currentTime, K key, int keyHash)
    {
        int index = _hashIndex(keyHash);
        // First, locate the entry, but keep track of position within hash/collision chain:
        CacheEntry<K,V> prev = null;
        CacheEntry<K,V> entry = _entries[index];
        int expireTime = _latestStaleTimestamp(currentTime);

        while (entry != null) {
            if ((entry._keyHash == keyHash) && _keyConverter.keysEqual(key, entry.getKey())) {
                // And if match, verify it is not stale
                if (entry._insertTime <= expireTime) { // if stale, remove
                    _removeEntry(entry, index, prev);
                    entry = null;
                } else { // if not stale, move as LRU
                    // Also: make this the LRU entry (note: _newEntryHead and _oldEntryHead are placeholders)
                    // first, unlink from previous chain
                    prev = entry._lessRecentEntry;
                    CacheEntry<K,V> next = entry._moreRecentEntry;
                    prev._moreRecentEntry = next;
                    next._lessRecentEntry = prev;
                    // then add as new head (wrt LRU)
                    next = _newEntryHead;
                    prev = _newEntryHead._lessRecentEntry;
                    prev._moreRecentEntry = entry;
                    entry._lessRecentEntry = prev;
                    next._moreRecentEntry = entry;
                    entry._moreRecentEntry = next;

                    // and finally, update match count; may be used to decide on promotion/demotion
                    entry._timesReturned += 1;
                }
                break;
            }
            prev = entry;
            entry = entry._nextCollision;
        }

        // also: if aggressively cleaning up, remove stale entries
        int count = _configInvalidatePerGet;
        while (count > 0 && _invalidateOldestIfStale(expireTime)) {
            --count;
        }
        return entry;
    }

    /**
     * Method for trying to remove entry with specified key. Returns removed
     * entry, if one found; otherwise returns null
     * 
     * @param timestamp Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     */
    public CacheEntry<K,V> removeEntry(long currentTime, K key)
    {
        return removeEntry(currentTime, key, _keyConverter.keyHash(key));
    }

    /**
     * Method for trying to remove entry with specified key. Returns removed
     * entry, if one found; otherwise returns null
     * 
     * @param timestamp Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     */
    public CacheEntry<K,V> removeEntry(long currentTime, K key, int keyHash)
    {
        int index = (keyHash & (_entries.length - 1));
        // First, locate the entry
        CacheEntry<K,V> prev = null;
        CacheEntry<K,V> entry = _entries[index];
        if (entry != null) {
            while (entry != null) {
                if ((entry._keyHash == keyHash) && _keyConverter.keysEqual(key, entry.getKey())) {
                    _removeEntry(entry, index, prev);
                    break;
                }
                prev = entry;
                entry = entry._nextCollision;
            }
        }
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

    /**
     * Method for putting specified entry in this cache; if an entry with the key
     * exists, it will be replaced.
     * 
     * @param timestamp Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Previous value for the key, if any; usually null but could be non-null
     *    for race condition cases
     */
    public CacheEntry<K,V> putEntry(long currentTime, K key, V value, int weight)
    {
        return putEntry(currentTime, key, _keyConverter.keyHash(key), value, weight);
    }
    
    /**
     * Method for putting specified entry in this cache; if an entry with the key
     * exists, it will be replaced.
     * 
     * @param timestamp Logical timestamp of point when this operation
     *   occurs; usually system time, but may be different for tests. Typically
     *   same for all parts of a single logical transaction (multi-level
     *   lookup or removal)
     * @param key Key of the entry to find value for
     * @param keyHash Hash code for the key
     * @param weight Combined weights of key and value, not including
     *    overhead of entry wrapper
     *    
     * @return Previous value for the key, if any; usually null but could be non-null
     *    for race condition cases
     */
    public CacheEntry<K,V> putEntry(long currentTime, K key, int keyHash,
            V value, int weight)
    {
        // First things first: let's see if there is an existing entry with key:
        int index = (keyHash & (_entries.length - 1));
        CacheEntry<K,V> prev = null;
        CacheEntry<K,V> existingEntry = _entries[index];
        if (existingEntry != null) {
            while (existingEntry != null) {
                if ((existingEntry._keyHash == keyHash) && _keyConverter.keysEqual(key, existingEntry.getKey())) {
                    _removeEntry(existingEntry, index, prev);
                    break;
                }
                prev = existingEntry;
                existingEntry = existingEntry._nextCollision;
            }
        }
        // Either way, need to add the new entry next, as newest and MRU
        CacheEntry<K,V> newEntry = new CacheEntry<K,V>(key, keyHash, value, _timeToTimestamp(currentTime), weight,
                _entries[index]);
        _entries[index] = newEntry;
        // ok; first insertion-order linked list:
        CacheEntry<K,V> next = _newEntryHead;
        prev = next._olderEntry;
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
        while ((count > 0) || (_currentEntries > _maxEntries) || (_currentContentsWeight > _maxContentsWeight)) {
            if (!_invalidateOldestIfStale(expireTime)) {
                break;
            }
            --count;
        }
        // And if we are still above limit, remove LRU entries
        count = 0;
        while ((_currentEntries > _maxEntries) || (_currentContentsWeight > _maxContentsWeight)) {
            CacheEntry<K,V> lru = _oldEntryHead._moreRecentEntry;
            if (lru == _newEntryHead) { // should never occur...
                throw new IllegalStateException("Flushed "+count+" entries, cache empty, still too many entries ("+_currentEntries
                        +") or too much weight ("+_currentContentsWeight+")");
            }
            _removeEntry(lru);
            ++count;
        }
        
        return existingEntry;
    }
    
    public void removaAll()
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
    /* Public API, stats
    /**********************************************************************
     */

    public final int size() { return _currentEntries; }

    /**
     * Returns crude estimated memory usage for entries cache contains, not
     * including overhead of cache itself (which is small); slightly
     * lower than what {@link #weight} would return.
     */
    public final long contentsWeight() { return _currentContentsWeight; }

    /**
     * Returns crude estimated memory usage for the cache as whole, including
     * contents
     */
    public final long weight() {
        return BASE_MEM_USAGE + _currentContentsWeight
            + (_entries.length * PlatformConstants.BASE_FIELD_MEMORY_USAGE);
    }
    
    public CacheStats getStats() {
        return new CacheStats(_hitCount, _missCount, _insertCount,
                size(), weight(),
                _maxEntries, _maxContentsWeight);
    }

    public void clearStats()
    {
        _hitCount = 0;
        _missCount = 0;
        _insertCount = 0;
    }

    /**
     * Method that can be used to decay existing statistics, to keep
     * smoothed-out average of hit/miss ratios over time.
     */
    public void decayStats(double ratio)
    {
        _hitCount = (int) (_hitCount * ratio);
        _missCount = (int) (_missCount * ratio);
        _insertCount = (int) (_insertCount * ratio);
    }
    
    /*
    /**********************************************************************
    /* Public methods, other
    /**********************************************************************
     */

    /**
     * Method for invalidating all stale entries this cache has (if any)
     * 
     * @param currentTimeMsecs Logical timestamp when this operation occurs
     * 
     * @return Number of stale entries invalidated
     */
    public int invalidateStale(long currentTimeMsecs)
    {
        return invalidateStale(currentTimeMsecs, Integer.MAX_VALUE);
    }
    
    public int invalidateStale(long currentTimeMsecs, int maxToInvalidate)
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
    /* Support for unit tests
    /**********************************************************************
     */
    
    protected CacheEntry<K,V> oldestEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        CacheEntry<K,V> oldest = _oldEntryHead._newerEntry;
        return (oldest != _newEntryHead) ? oldest : null;
    }

    protected CacheEntry<K,V> newestEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        CacheEntry<K,V> newest = _newEntryHead._olderEntry;
        return (newest != _oldEntryHead) ? newest : null;
    }

    protected CacheEntry<K,V> leastRecentEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        CacheEntry<K,V> leastRecent = _oldEntryHead._moreRecentEntry;
        return (leastRecent != _newEntryHead) ? leastRecent : null;
    }

    protected CacheEntry<K,V> mostRecentEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        CacheEntry<K,V> mostRecent = _newEntryHead._lessRecentEntry;
        return (mostRecent != _oldEntryHead) ? mostRecent : null;
    }

    protected List<K> keysFromOldestToNewest()
    {
        ArrayList<K> keys = new ArrayList<K>();
        for (CacheEntry<K,V> entry = _oldEntryHead._newerEntry; entry != _newEntryHead; entry = entry._newerEntry) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    protected List<K> keysFromLeastToMostRecent()
    {
        ArrayList<K> keys = new ArrayList<K>();
        for (CacheEntry<K,V> entry = _oldEntryHead._moreRecentEntry; entry != _newEntryHead; entry = entry._moreRecentEntry) {
            keys.add(entry.getKey());
        }
        return keys;
    }
    
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected final static int _timeToTimestamp(long currentTimeMsecs)
    {
        int time = (int) (currentTimeMsecs >> 8); // divide by 256, to quarter-seconds
        if (time < 0) {
            time &= 0x7FFFFFFF;
        }
        return time;
    }

    private final int _hashIndex(int keyHash) {
        return keyHash & (_entries.length - 1);
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
        CacheEntry<K,V> oldest = _oldEntryHead._newerEntry;
        if (oldest != _newEntryHead) {
            /* ok now: timestamps we use are relative (due to truncation), so
             * we MUST compare by subtraction, compare difference
             */
            int diff = oldest._insertTime - latestStaleTime;
            if (diff <= 0) { // created at or before expiration time (latest timestamp that is stale)
                --_currentEntries;
                _currentContentsWeight -= oldest._weight;
                oldest.unlink();
                return true;
            }
        }
        return false;
    }

    protected boolean _invalidateOldest()
    {
        CacheEntry<K,V> oldest = _oldEntryHead._newerEntry;
        if (oldest != _newEntryHead) {
            --_currentEntries;
            _currentContentsWeight -= oldest._weight;
            oldest.unlink();
            return true;
        }
        return false;
    }
    
    protected void _removeEntry(CacheEntry<K,V> entry)
    {
        // Ok, need to locate entry in hash...
        int index = _hashIndex(entry._keyHash);
        CacheEntry<K,V> curr = _entries[index];
        CacheEntry<K,V> prev = null;
        while (curr != null) {
            if (curr == entry) {
                _removeEntry(entry, index, prev);
                return;
            }
            prev = curr;
            curr = curr._nextCollision;
        }
        // should never occur, so:
        throw new IllegalStateException("Internal data error: could not find entry (index "+index+"/"+_entries.length+"), key "+entry.getKey());
    }
    
    protected void _removeEntry(CacheEntry<K,V> entry, int hashIndex, CacheEntry<K,V> prevInHash)
    {
        // First, update counts
        --_currentEntries;
        _currentContentsWeight -= entry._weight;

        // Unlink from hash area
        CacheEntry<K,V> next = entry._nextCollision;
        if (prevInHash == null) {
            _entries[hashIndex] = next;
        } else {
            prevInHash._nextCollision = next;
        }
        // and from linked lists:
        entry.unlink();
    }

    private void _resetOldestAndNewest()
    {
        _newEntryHead = new CacheEntry<K,V>();
        _oldEntryHead = new CacheEntry<K,V>();
        _newEntryHead._olderEntry = _oldEntryHead;
        _newEntryHead._lessRecentEntry = _oldEntryHead;
        _oldEntryHead._newerEntry = _newEntryHead;
        _oldEntryHead._moreRecentEntry = _newEntryHead;
    }
}
