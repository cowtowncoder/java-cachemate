package com.fasterxml.cachemate;

public abstract class KeyConverter<K>
{
    public abstract boolean keysEqual(K key1, K key2);
}
