package com.fasterxml.cachemate;

/**
 * Entry for cache; contains both key and value, to reduce number
 * of object instances needed. Also provides metadata useful for
 * caller.ache
 */
public class CacheEntry<K, V>
{
    // // // Constants
    
    /**
     * This is our guestimation of per-entry base overhead JVM incurs; it is used
     * to get closer approximation of true memory usage of cache structure.
     * We will use 16 bytes for base object, and otherwise typical 32-bit system
     * values for 11 fields we have. This gives estimation of 60 bytes; not
     * including referenced objects (key, value)
     */
    public final static int MEM_USAGE_PER_ENTRY = 16 + (11 * 4);

    // // // Contents

    /**
     * Entry key
     */
    protected final K _key;

    /**
     * Hash code of the key value (since it is possible
     * that hashCode of key itself is not useful, as is the case
     * for byte[])
     */
    protected final int _keyHash;

    /**
     * Entry value;
     */
    protected final V _value;

    // // // Size, staleness
    
    /**
     * Timepoint when entry was added in cache, in units of 256 milliseconds
     * (about quarter of a second). Used for staleness checks.
     */
    protected final int _insertTime;
    
    /**
     * Weight of this entry, including both key and value
     */
    protected final int _weight;

    // // // Linked lists
    
    /**
     * Link to entry added right after this entry; never null, but may point
     * to placeholder
     */
    protected CacheEntry<K, V> _newerEntry;

    /**
     * Link to entry that was added right before this entry; never null, but may point
     * to placeholder
     */
    protected CacheEntry<K, V> _olderEntry;

    /**
     * Entry that was more recently accessed than this entry;
     * never null but may point to a placeholder entry
     */
    protected CacheEntry<K, V> _moreRecentEntry;

    /**
     * Entry that was less recently accessed than this entry
     */
    protected CacheEntry<K, V> _lessRecentEntry;

    /**
     * Link to next entry in collision list; will have hash code that resulted
     * in same bucket as this entry. Null if no collisions for bucket
     */
    protected CacheEntry<K, V> _nextCollision;

    // // // Statistics
    
    /**
     * Number of times this entry has been succesfully retrieved from
     * the cache; may be used to decide if entry is to be promoted/demoted,
     * in addition to basic LRU ordering
     */
    protected int _timesReturned;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */
    
    /**
     * Constructors used for constructing placeholder instances (heads and tails
     * of linked lists)
     */
    public CacheEntry() {
        this(null, 0, null, 0, 0, null);
    }
    
    public CacheEntry(K key, int keyHash, V value, int insertTime, int weight,
            CacheEntry<K,V> nextCollision)
    {
        _key = key;
        _value = value;
        _keyHash = keyHash;
        _insertTime = insertTime;
        _weight = weight;
        _nextCollision = nextCollision;
    }

    public void linkNextNewer(CacheEntry<K,V> next)
    {
        if (_newerEntry != null) { // sanity check
            throw new IllegalStateException("Already had entry with key "+_key+" (hash code 0x"+Integer.toHexString(_keyHash)+")");
        }
        _newerEntry = next;
    }

    /*
    /**********************************************************************
    /* Public accessors
    /**********************************************************************
     */

    public K getKey() { return _key; }

    public V getValue() { return _value; }

    /*
    /**********************************************************************
    /* Standard methods
    /**********************************************************************
     */

    @Override
    public int hashCode() { return _key.hashCode(); }
}
