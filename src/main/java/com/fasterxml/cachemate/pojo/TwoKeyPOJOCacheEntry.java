package com.fasterxml.cachemate.pojo;

import com.fasterxml.cachemate.TwoKeyCacheEntry;

public class TwoKeyPOJOCacheEntry<K1,K2,V>
    extends POJOCacheEntryBase<K1, V, TwoKeyPOJOCacheEntry<K1,K2,V>>
    implements TwoKeyCacheEntry<K1,K2,V>
{
    /*
    /**********************************************************************
    /* Additional contents
    /**********************************************************************
     */

    /**
     * Secondary key of the entry
     */
    protected final K2 _key2;

    /**
     * Hash code of the secondary key (since it is possible
     * that hashCode of _key itself is not useful, as is the case
     * for byte[])
     */
    protected final int _keyHash2;

    /*
    /**********************************************************************
    /* Additional linkage for secondary key
    /**********************************************************************
     */

    /**
     * Link to next entry in secondary collision list; will have hash code that resulted
     * in same bucket as this entry. Null if no collisions for bucket
     */
    protected TwoKeyPOJOCacheEntry<K1, K2, V> _secondaryCollision;
    
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */
    
    /**
     * Constructors used for constructing placeholder instances (heads and tails
     * of linked lists)
     */
    public TwoKeyPOJOCacheEntry() {
        this(null, 0, null, 0,
                null, 0, 0, null);
    }
    
    public TwoKeyPOJOCacheEntry(K1 key, int keyHash, K2 key2, int keyHash2,
            V value, int insertTime, int weight,
            TwoKeyPOJOCacheEntry<K1,K2,V> nextCollision)
    {
        super(key, keyHash, value, insertTime, weight, nextCollision);
        _key2 = key2;
        _keyHash2 = keyHash;
    }
    
    /*
    /**********************************************************************
    /* Public API implementation
    /**********************************************************************
     */
    
    @Override
    public K2 getSecondaryKey() {
        return _key2;
    }

    @Override
    public final int getSecondaryKeyHash() { return _keyHash2; }

    /*
    /**********************************************************************
    /* Standard method overrides
    /**********************************************************************
     */
    
    @Override
    public final String toString() {
        return new StringBuilder().append('[').append(getKey())
            .append('/').append(getSecondaryKey())
            .append("]:").append(getValue()).toString();
    }
    
    /*
    /**********************************************************************
    /* Package methods
    /**********************************************************************
     */

    @Override
    protected void unlink()
    {
        super.unlink();
        // !!! TODO:
    }
}
