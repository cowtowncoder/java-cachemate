package com.fasterxml.cachemate;

/**
 * Entry for caches that can be accessed both by using the primary
 * key and an optional secondary key.
 */
public interface TwoKeyCacheElement<K1, K2, V>
    extends CacheElement<K1, V>
{
    /*
    /**********************************************************************
    /* Co-variant versions of methods from CacheElement
    /**********************************************************************
     */

    @Override
    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, K1 key, V value, int weight);

    @Override
    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, int timeToLiveSecs, K1 key, V value, int weight);
    
    @Override
    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, K1 key, int keyHash,
            V value, int weight);

    @Override
    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, int timeToLiveSecs, K1 key, int keyHash,
            V value, int weight);
    
    @Override
    public TwoKeyCacheEntry<K1, K2, V> findEntry(long currentTime, K1 key);

    @Override
    public TwoKeyCacheEntry<K1, K2, V> findEntry(long currentTime, K1 key, int keyHash);

    @Override
    public TwoKeyCacheEntry<K1, K2, V> removeEntry(long currentTime, K1 key);

    @Override
    public TwoKeyCacheEntry<K1, K2, V> removeEntry(long currentTime, K1 key, int keyHash);
    
    /*
    /**********************************************************************
    /* Additional methods using secondary key.
    /* Note that no additional remove methods are exposed since secondary
    /* keys are not guaranteed to be unique; to remove, need to combine
    /* lookup (to get primary key) with removal
    /**********************************************************************
     */

    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, K1 primaryKey, K2 secondaryKey, V value, int weight);
    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, int timeToLiveSecs,
            K1 primaryKey, K2 secondaryKey, V value, int weight);

    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, K1 primaryKey, int primaryKeyHash,
            K2 secondaryKey, int secondaryKeyHash,
            V value, int weight);
    public TwoKeyCacheEntry<K1, K2, V> putEntry(long currentTime, int timeToLiveSecs,
            K1 primaryKey, int primaryKeyHash,
            K2 secondaryKey, int secondaryKeyHash,
            V value, int weight);
    
    public TwoKeyCacheEntry<K1, K2, V> findEntryBySecondary(long currentTime, K2 secondaryKey);
    public TwoKeyCacheEntry<K1, K2, V> findEntryBySecondary(long currentTime, K2 secondaryKey, int keyHash);
}
