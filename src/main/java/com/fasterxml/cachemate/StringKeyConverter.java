package com.fasterxml.cachemate;

public class StringKeyConverter extends KeyConverter<String>
{
    private final static int BASE_MEM_USAGE = PlatformConstants.BASE_OBJECT_MEMORY_USAGE 
        + (2 * PlatformConstants.BASE_FIELD_MEMORY_USAGE);

    public final static StringKeyConverter instance = new StringKeyConverter();
    
    @Override
    public int keyHash(String key) {
        return key.hashCode();
    }

    @Override
    public int keyWeight(String key)
    {
        // chars take 2 bytes, so:
        return BASE_MEM_USAGE + (key.length() << 1);
    }

    @Override
    public boolean keysEqual(String key1, String key2) {
        return (key1 == key2) || key1.equals(key2);
    }

}
