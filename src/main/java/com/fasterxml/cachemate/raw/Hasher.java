package com.fasterxml.cachemate.raw;

/**
 * Abstract class that defines interface for providers of hash codes, used
 * for "raw" keys.
 */
public abstract class Hasher
{
    /**
     * Method called to calculate hash value over given byte sequence.
     */
    public abstract int calcHash(byte[] data, int offset, int length);
}
