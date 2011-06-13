package com.fasterxml.cachemate.pojo;

import com.fasterxml.cachemate.CacheEntry;

/*
 * Shared base class for various {@link CacheEntry}
 * implementations.
 *<p>
 * Note the interesting generic declaration: it is mostly just needed
 * to support type-safe linkage for sub-classes
 */
abstract class POJOCacheEntryBase<K, V, SUBTYPE extends POJOCacheEntryBase<K, V, SUBTYPE>>
    implements CacheEntry<K, V>
{
    /*
    /**********************************************************************
    /* Primary contents
    /**********************************************************************
     */

    /**
     * Primary key of the entry
     */
    protected final K _key;

    /**
     * Hash code of the _key value (since it is possible
     * that hashCode of _key itself is not useful, as is the case
     * for byte[])
     */
    protected final int _keyHash;

    /**
     * Value that entry contains
     */
    protected final V _value;

    /*
    /**********************************************************************
    /* Links between entries; LRU, expiration
    /**********************************************************************
     */

    /**
     * Link to entry added right after this entry; never null, but may point
     * to placeholder
     */
    protected SUBTYPE _newerEntry;

    /**
     * Link to entry that was added right before this entry; never null, but may point
     * to placeholder
     */
    protected SUBTYPE _olderEntry;

    /**
     * Entry that was more recently accessed than this entry;
     * never null but may point to a placeholder entry
     */
    protected SUBTYPE _moreRecentEntry;

    /**
     * Entry that was less recently accessed than this entry
     */
    protected SUBTYPE _lessRecentEntry;

    /*
    /**********************************************************************
    /* Links between entries; primary hash table
    /**********************************************************************
     */

    /**
     * Link to next entry in collision list; will have hash code that resulted
     * in same bucket as this entry. Null if no collisions for bucket
     */
    protected SUBTYPE _primaryCollision;
    
    /*
    /**********************************************************************
    /* Metadata
    /**********************************************************************
     */

    /**
     * Timepoint when entry was added in cache, in units of 256 milliseconds
     * (about quarter of a second). Used for staleness checks.
     */
    protected final int _insertionTime;
    
    /**
     * Weight of this entry, including both _key and value
     */
    protected final int _weight;
    
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
    
    protected POJOCacheEntryBase(K key, int keyHash, V value, int insertTime, int weight,
            SUBTYPE nextCollision)
    {
        _key = key;
        _value = value;
        _keyHash = keyHash;
        _insertionTime = insertTime;
        _weight = weight;
        _primaryCollision = nextCollision;
    }

    public void linkNextNewer(SUBTYPE next)
    {
        if (_newerEntry != null) { // sanity check
            throw new IllegalStateException("Already had entry with _key "+_key+" (hash code 0x"+Integer.toHexString(_keyHash)+")");
        }
        _newerEntry = next;
    }
    
    /*
    /**********************************************************************
    /* Public accessors, key/value
    /**********************************************************************
     */

    @Override
    public final K getKey() { return _key; }

    @Override
    public final V getValue() { return _value; }

    @Override
    public final int getKeyHash() { return _keyHash; }

    @Override
    public final int getWeight() {
        return _weight;
    }
    
    /*
    /**********************************************************************
    /* Public accessors, metadata
    /**********************************************************************
     */

    @Override
    public long getAgeInMilliSeconds(long currentTime)
    {
        // we keep track of time in units of 256 milliseconds (to conserve memory):
        long created = ((long) _insertionTime) << 8;
        long diff = currentTime - created;
        return (diff < 0L) ? 0L : diff;
    }

    
    /*
    /**********************************************************************
    /* Standard method overrides
    /**********************************************************************
     */
    
    @Override
    public int hashCode() { return _keyHash; }

    @Override
    public abstract String toString();

    /*
    /**********************************************************************
    /* Package access
    /**********************************************************************
     */

    protected final SUBTYPE newerEntry() { return _newerEntry; }
    protected final SUBTYPE olderEntry() { return _olderEntry; }
    protected final SUBTYPE moreRecentEntry() { return _moreRecentEntry; }
    protected final SUBTYPE lessRecentEntry() { return _lessRecentEntry; }
    
    /**
     * Method used to unlink entries from LRU/expiration chains; but NOT
     * from collision chains.
     */
    protected void unlink()
    {
        // first unlink from oldest/newest linkage
        SUBTYPE prev = _olderEntry;
        SUBTYPE next = _newerEntry;
        
        prev._newerEntry = next;
        next._olderEntry = prev;

        // then from most/least recent linkage
        prev = _lessRecentEntry;
        next = _moreRecentEntry;
        
        prev._moreRecentEntry = next;
        next._lessRecentEntry = prev;
    }

}
