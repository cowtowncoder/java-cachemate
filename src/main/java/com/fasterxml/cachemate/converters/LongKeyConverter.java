package com.fasterxml.cachemate.converters;

import com.fasterxml.cachemate.KeyConverter;
import com.fasterxml.cachemate.PlatformConstants;

public class LongKeyConverter extends KeyConverter<Long>
{
    // long fields occupy two fields (on 32-bit machine at least)
    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (2 * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

    public final static LongKeyConverter instance = new LongKeyConverter();
    
    @Override
    public int keyHash(Long key) {
        // should we do the shuffle? (murmur, jenkins, something?)
        return key.hashCode();
    }

    @Override
    public int keyWeight(Long key)
    {
        return BASE_MEM_USAGE;
    }

    @Override
    public boolean keysEqual(Long key1, Long key2) {
        return key1.longValue() == key2.longValue();
    }

}
