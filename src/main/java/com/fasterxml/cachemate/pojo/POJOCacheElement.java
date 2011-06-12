package com.fasterxml.cachemate.pojo;

import java.util.*;

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

    // // // Configuration

    protected final KeyConverter<K> _keyConverter;

    // // // Actual entries

    /**
     * Primary hash area for entries
     */
    protected POJOCacheEntry<K, V>[] _entries;
    
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
        _keyConverter = keyConverter;
        _maxEntries = maxEntries;
        _resetOldestAndNewest(); // to set oldest/newest (head/tail) linked
        // array needs to be a power of two, just find next bigger:
        int hashAreaSize = 16;
        
        while (hashAreaSize < maxEntries) {
            hashAreaSize += hashAreaSize;
        }
        _entries = (POJOCacheEntry<K,V>[]) new POJOCacheEntry<?,?>[hashAreaSize];

        // take into account base mem usage of the cache (crude, but...), including hash area
        _maxContentsWeight = maxWeight - BASE_MEM_USAGE - (hashAreaSize * PlatformConstants.BASE_FIELD_MEMORY_USAGE);
        _configTimeToLive = (int) ((1000L * timeToLiveSecs) >> 8);
    }
    
    /*
    /**********************************************************************
    /* Public API, basic access
    /**********************************************************************
     */

    @Override
    public POJOCacheEntry<K,V> findEntry(long currentTime, K key) {
        return findEntry(currentTime, key, _keyConverter.keyHash(key));
    }
    
    @Override
    public POJOCacheEntry<K,V> findEntry(long currentTime, K key, int keyHash)
    {
        int index = _hashIndex(keyHash);
        // First, locate the entry, but keep track of position within hash/collision chain:
        POJOCacheEntry<K,V> prev = null;
        POJOCacheEntry<K,V> entry = _entries[index];
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
                    POJOCacheEntry<K,V> next = entry._moreRecentEntry;
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
    public POJOCacheEntry<K,V> removeEntry(long currentTime, K key) {
        return removeEntry(currentTime, key, _keyConverter.keyHash(key));
    }

    @Override
    public POJOCacheEntry<K,V> removeEntry(long currentTime, K key, int keyHash)
    {
        int index = (keyHash & (_entries.length - 1));
        // First, locate the entry
        POJOCacheEntry<K,V> prev = null;
        POJOCacheEntry<K,V> entry = _entries[index];
        if (entry != null) {
            while (entry != null) {
                if ((entry._keyHash == keyHash) && _keyConverter.keysEqual(key, entry.getKey())) {
                    _removeEntry(entry, index, prev);
                    break;
                }
                prev = entry;
                entry = entry._primaryCollision;
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

    @Override
    public POJOCacheEntry<K,V> putEntry(long currentTime, K key, V value, int weight) {
        return putEntry(currentTime, key, _keyConverter.keyHash(key), value, weight);
    }
    
    @Override
    public POJOCacheEntry<K,V> putEntry(long currentTime, K key, int keyHash,
            V value, int weight)
    {
        // First things first: let's see if there is an existing entry with _key:
        int index = (keyHash & (_entries.length - 1));
        POJOCacheEntry<K,V> prev = null;
        POJOCacheEntry<K,V> existingEntry = _entries[index];
        if (existingEntry != null) {
            while (existingEntry != null) {
                if ((existingEntry._keyHash == keyHash) && _keyConverter.keysEqual(key, existingEntry.getKey())) {
                    _removeEntry(existingEntry, index, prev);
                    break;
                }
                prev = existingEntry;
                existingEntry = existingEntry._primaryCollision;
            }
        }
        // Either way, need to add the new entry next, as newest and MRU
        POJOCacheEntry<K,V> newEntry = new POJOCacheEntry<K,V>(key, keyHash, value, _timeToTimestamp(currentTime), weight,
                _entries[index]);
        _entries[index] = newEntry;
        // ok; first insertion-order linked list:
        POJOCacheEntry<K,V> next = _newEntryHead;
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
            POJOCacheEntry<K,V> lru = _oldEntryHead._moreRecentEntry;
            if (lru == _newEntryHead) { // should never occur...
                throw new IllegalStateException("Flushed "+count+" entries, cache empty, still too many entries ("+_currentEntries
                        +") or too much weight ("+_currentContentsWeight+")");
            }
            _removeEntry(lru);
            ++count;
        }
        return existingEntry;
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
    /* Public methods, other
    /**********************************************************************
     */
    
    @Override
    public int invalidateStale(long currentTimeMsecs, int maxToInvalidate)
    {
        int count = 0;
        int earliestNonStale = _latestStaleTimestamp(currentTimeMsecs);
        
        while (count < maxToInvalidate && _invalidateOldestIfStale(earliestNonStale)) {
            ++count;
        }
        return count;
    }

    @Override
    public final long weight() {
        return BASE_MEM_USAGE + _currentContentsWeight
            + (_entries.length * PlatformConstants.BASE_FIELD_MEMORY_USAGE);
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
    
    /*
    /**********************************************************************
    /* Support for unit tests
    /**********************************************************************
     */
    
    protected POJOCacheEntry<K,V> oldestEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        POJOCacheEntry<K,V> oldest = _oldEntryHead._newerEntry;
        return (oldest != _newEntryHead) ? oldest : null;
    }

    protected POJOCacheEntry<K,V> newestEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        POJOCacheEntry<K,V> newest = _newEntryHead._olderEntry;
        return (newest != _oldEntryHead) ? newest : null;
    }

    protected POJOCacheEntry<K,V> leastRecentEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        POJOCacheEntry<K,V> leastRecent = _oldEntryHead._moreRecentEntry;
        return (leastRecent != _newEntryHead) ? leastRecent : null;
    }

    protected POJOCacheEntry<K,V> mostRecentEntry(long currentTime)
    {
        // first, ensure we have dumped all stale entries, then return what's left if anything
        while (_invalidateOldestIfStale(_latestStaleTimestamp(currentTime))) { }
        POJOCacheEntry<K,V> mostRecent = _newEntryHead._lessRecentEntry;
        return (mostRecent != _oldEntryHead) ? mostRecent : null;
    }

    protected List<K> keysFromOldestToNewest()
    {
        ArrayList<K> keys = new ArrayList<K>();
        for (POJOCacheEntry<K,V> entry = _oldEntryHead._newerEntry; entry != _newEntryHead; entry = entry._newerEntry) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    protected List<K> keysFromLeastToMostRecent()
    {
        ArrayList<K> keys = new ArrayList<K>();
        for (POJOCacheEntry<K,V> entry = _oldEntryHead._moreRecentEntry; entry != _newEntryHead; entry = entry._moreRecentEntry) {
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
        POJOCacheEntry<K,V> oldest = _oldEntryHead._newerEntry;
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
        POJOCacheEntry<K,V> oldest = _oldEntryHead._newerEntry;
        if (oldest != _newEntryHead) {
            _removeEntry(oldest);
            return true;
        }
        return false;
    }
    
    protected void _removeEntry(POJOCacheEntry<K,V> entry)
    {
        // Ok, need to locate entry in hash...
        int index = _hashIndex(entry._keyHash);
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

    private void _resetOldestAndNewest()
    {
        _newEntryHead = new POJOCacheEntry<K,V>();
        _oldEntryHead = new POJOCacheEntry<K,V>();
        _newEntryHead._olderEntry = _oldEntryHead;
        _newEntryHead._lessRecentEntry = _oldEntryHead;
        _oldEntryHead._newerEntry = _newEntryHead;
        _oldEntryHead._moreRecentEntry = _newEntryHead;
    }
}
