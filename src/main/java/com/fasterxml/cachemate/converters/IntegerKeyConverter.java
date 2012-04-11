package com.fasterxml.cachemate.converters;

import com.fasterxml.cachemate.util.PlatformConstants;

public class IntegerKeyConverter extends KeyConverter<Integer>
{
    // I _think_ Integers manage to squeeze all data in just one field (or maybe even base overhead?)
    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (1 * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

    public final static IntegerKeyConverter instance = new IntegerKeyConverter();
    
    @Override
    public int keyHash(Integer key) {
        // should we do the shuffle? (murmur, jenkins, something?)
        return key.hashCode();
    }

    @Override
    public int keyWeight(Integer key)
    {
        return BASE_MEM_USAGE;
    }

    @Override
    public boolean keysEqual(Integer key1, Integer key2) {
        return key1.intValue() == key2.intValue();
    }

}
