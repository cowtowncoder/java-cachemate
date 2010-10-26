package com.fasterxml.cachemate;

import com.fasterxml.cachemate.converters.StringKeyConverter;

import junit.framework.TestCase;

public class TestBoundedLRUCache extends TestCase
{
    /**
     * Unit test to verify that cache starts empty and with specific settings.
     */
    public void testInitialState() throws Exception
    {
        // Use Strings as keys, values
        BoundedLRUCacheElement<String,String> cache = new BoundedLRUCacheElement<String,String>(StringKeyConverter.instance,
                64, 64 * 1024, /* ttl */ 4);
        // base size; no entries, no content memory usage
        assertEquals(0, cache.size());
        assertEquals(0, cache.contentsWeight());
        // should have some basic overhead, esp. with 64-entry hash area
        long w = cache.weight();
        assertTrue("Initial weight should > 256, got "+w, w > 256L);

        // shouldn't find anything:
        long time = 3000L; // at "3 seconds"
        assertNull(cache.findEntry(time, "a"));
        assertNull(cache.findEntry(time, "foobar"));
        // nor be able to remove anything (but shouldn't fail either)
        assertNull(cache.removeEntry(time, "a"));
    }    
}