package com.fasterxml.cachemate;

public interface TwoKeyCacheEntry<K1,K2,V>
    extends CacheEntry<K1,V>
{
    /**
     * Accessor for getting secondary key of this entry; since only
     * primary key (accessed with {@link #getKey}) is mandatory,
     * this may return null.
     */
    public K2 getSecondaryKey();

    /**
     * Accessor for hash value of the secondary key; or 0
     * if there is no secondary key
     */
    public int getSecondaryKeyHash();
}
