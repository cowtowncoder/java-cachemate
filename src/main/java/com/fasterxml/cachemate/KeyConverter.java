package com.fasterxml.cachemate;

/**
 * Interface that defines object that is responsible for conversions
 * and other related operations needed for using keys. Reason a separate
 * interface is needed is because many common _key types can not be
 * extended or do not come with efficient implementations for things
 * like hash code calculations; for example, byte array objects do not
 * have useful <code>hashCode</code> or <code>equals</code> implementations.
 */
public abstract class KeyConverter<K>
{
    /**
     * Method for comparing two keys for equality
     */
    public abstract boolean keysEqual(K key1, K key2);

    /**
     * Method for calculating hash code for given _key
     */
    public abstract int keyHash(K key);

    /**
     * Method for estimating weight of given _key, in units of
     * bytes (rought estimate of memory usage)
     */
    public abstract int keyWeight(K key);
}
