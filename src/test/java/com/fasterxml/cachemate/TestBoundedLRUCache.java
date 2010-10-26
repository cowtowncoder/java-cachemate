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

        assertNull(cache.oldestEntry(time));
        assertNull(cache.newestEntry(time));
        assertNull(cache.leastRecentEntry(time));
        assertNull(cache.mostRecentEntry(time));
    }    

    /**
     * Test for verifying state with 3 basic inserts
     */
    public void testSimpleInserts() throws Exception
    {
        BoundedLRUCacheElement<String,String> cache = new BoundedLRUCacheElement<String,String>(StringKeyConverter.instance,
                64, 64 * 1024, /* ttl */ 4);
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, "abc", "def", 3));
        assertEquals(1, cache.size());
        assertEquals(3, cache.contentsWeight());

        // basic checks after first insert
        CacheEntry<String,String> entry = cache.oldestEntry(time);
        assertNotNull(entry);
        assertEquals("abc", entry.getKey());
        assertEquals("def", entry.getValue());
        
        entry = cache.newestEntry(time);
        assertEquals("abc", entry.getKey());
        entry = cache.leastRecentEntry(time);
        assertEquals("abc", entry.getKey());
        entry = cache.mostRecentEntry(time);
        assertEquals("abc", entry.getKey());

        // Then two more simple inserts
        assertNull(cache.putEntry(time, "12", "34", 4));
        assertNull(cache.putEntry(time, "xxx", "y", 5));
        assertEquals(3, cache.size());
        assertEquals(12, cache.contentsWeight());

        assertEquals("abc", cache.oldestEntry(time).getKey());
        // no access, so this is still the same
        assertEquals("abc", cache.leastRecentEntry(time).getKey());
        assertEquals("xxx", cache.newestEntry(time).getKey());
        assertEquals("xxx", cache.mostRecentEntry(time).getKey());
    }
    
    public void testSimpleAccess() throws Exception
    {
        BoundedLRUCacheElement<String,String> cache = new BoundedLRUCacheElement<String,String>(StringKeyConverter.instance,
                64, 64 * 1024, /* ttl */ 4);
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, "abc", "def", 3));

        time += 1000L; // 4 seconds
        assertNull(cache.putEntry(time, "12", "34", 4));
        time += 1000L; // 5 seconds
        assertNull(cache.putEntry(time, "xxx", "y", 5));

        // Starting state:
        CacheEntry<String,String> entry = cache.oldestEntry(time);
        assertNotNull(entry);
        assertEquals("abc", entry.getKey());
        assertEquals("abc", cache.leastRecentEntry(time).getKey());
        assertEquals("xxx", cache.newestEntry(time).getKey());
        assertEquals("xxx", cache.mostRecentEntry(time).getKey());
        
        // with access, should change
        assertEquals("34", cache.findEntry(time, "12"));
        // oldest/newest do not change
        assertEquals("abc", cache.oldestEntry(time).getKey());
        assertEquals("xxx", cache.newestEntry(time).getKey());
        // but most recent does
        assertEquals("abc", cache.leastRecentEntry(time).getKey());
        assertEquals("34", cache.mostRecentEntry(time).getKey());
    }
}