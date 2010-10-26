package com.fasterxml.cachemate.converters;

import com.fasterxml.cachemate.KeyConverter;
import com.fasterxml.cachemate.PlatformConstants;

public class ByteKeyConverter extends KeyConverter<byte[]>
{
    public final static ByteKeyConverter instance = new ByteKeyConverter();

    @Override
    public int keyHash(byte[] key)
    {
        // Let's just use impl from String?
        int len = key.length;
        if (len == 0) {
            return 1;
        }
        int hash = key[0];
        for (int i = 1; i < len; ++i) {
            len = (len * 31) +key[i];
        }
        return hash;
    }

    @Override
    public int keyWeight(byte[] key)
    {
        return PlatformConstants.BASE_OBJECT_MEMORY_USAGE + key.length;
    }

    @Override
    public boolean keysEqual(byte[] key1, byte[] key2)
    {
        if (key1 == key2) {
            return true;
        }
        int len = key1.length;
        if (key2.length != len) {
            return false;
        }
        for (int i = 0; i < len; ++i) {
            if (key1[i] != key2[i]) {
                return false;
            }
        }
        return false;
    }

}
