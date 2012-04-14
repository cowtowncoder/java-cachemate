package com.fasterxml.cachemate.raw;

import com.fasterxml.cachemate.CacheEntry;
import com.fasterxml.cachemate.util.TimeUtil;

public class RawCacheEntry implements CacheEntry<byte[], byte[]>
{
    private final int _keyHash;

    private final byte[] _key, _value;
    
    /**
     * Expiration timestamp from the entry 
     */
    private final int _expirationTime;
    
    public RawCacheEntry(int keyHash, byte[] key, byte[] value,
            int expirationTime)
    {
        _keyHash = keyHash;
        _key = key;
        _value = value;
        _expirationTime = expirationTime;
    }

    @Override
    public byte[] getKey() {
        return _key;
    }

    @Override
    public int getKeyHash() {
        return _keyHash;
    }

    @Override
    public byte[] getValue() {
        return _value;
    }

    // not really relevant to raw entries but:
    @Override
    public int getWeight() {
        return _key.length + _value.length;
    }

    @Override
    public long getExpirationInMilliSeconds(long currentTime) {
        return TimeUtil.getExpirationInMilliSeconds(currentTime, _expirationTime);
    }
}
