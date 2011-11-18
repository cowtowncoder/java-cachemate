package com.fasterxml.cachemate.pojo;

import java.util.*;

import com.fasterxml.cachemate.CacheEntry;
import com.fasterxml.cachemate.converters.StringKeyConverter;
import com.fasterxml.cachemate.pojo.POJOCacheElement;

/**
 * Unit tests verifying correct functioning of {@link POJOCacheElement}
 * as stand-alone cache component.
 */
public class TestPOJOCacheElement extends POJOTestBase
{
    /**
     * Unit test to verify that cache starts empty and with specific settings.
     */
    public void testInitialState() throws Exception
    {
        // Use Strings as keys, values
        POJOCacheElement<String,String> cache = new POJOCacheElement<String,String>(StringKeyConverter.instance,
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

        cache.checkSanity();
    }    

    /**
     * Test for verifying state with 3 basic inserts
     */
    public void testSimpleInserts() throws Exception
    {
        POJOCacheElement<String,String> cache = new POJOCacheElement<String,String>(StringKeyConverter.instance,
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

        cache.checkSanity();
    }

    /**
     * Test for ensuring state remains valid when insertion leads to
     * removal due to size constraints
     */
    public void testInsertWithOverflow() throws Exception
    {
        // no time-based expiration (1000 seconds)
        POJOCacheElement<String,String> cache = new POJOCacheElement<String,String>(StringKeyConverter.instance,
                3, 3 * 1024, 1000);
        // invalidate one entry per get-operation
        cache.setConfigInvalidatePerGet(1);
        
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, "abc", "def", 3));
        assertEquals(1, cache.size());
        assertEquals(3, cache.contentsWeight());
        ++time;
        
        assertNull(cache.putEntry(time, "bcd", "123", 4));
        assertEquals(2, cache.size());
        assertEquals(7, cache.contentsWeight());
        ++time;

        assertNull(cache.putEntry(time, "cde", "3", 5));
        assertEquals(3, cache.size());
        assertEquals(12, cache.contentsWeight());
        ++time;

        // this should result in removal of the first entry, "abc"
        assertNull(cache.putEntry(time, "def", "x", 6));
        assertEquals(3, cache.size());
        assertEquals(15, cache.contentsWeight());
        ++time;

        // this for second entry
        assertNull(cache.putEntry(time, "efg", "x", 2));
        assertEquals(3, cache.size());
        assertEquals(13, cache.contentsWeight());
        ++time;

        // and this for the third original entry
        assertNull(cache.putEntry(time, "fgh", "x", 1));
        assertEquals(3, cache.size());
        assertEquals(9, cache.contentsWeight());
        ++time;

        // finally, let's move ahead in time, forcing expiration when doing gets
        // (configured to do at most one removal per get)
        time = 3000L * (1001L * 1000L);

        assertNull(cache.findEntry(time, "abc"));
        assertEquals(2, cache.size());
        assertEquals(3L, cache.contentsWeight());
        assertNull(cache.findEntry(time, "abc"));
        assertEquals(1, cache.size());
        assertEquals(1L, cache.contentsWeight());

        // but how about mixing with inserts?
        assertNull(cache.putEntry(time, "xxx", "yyy", 9));
        assertEquals(1, cache.size());
        assertEquals(9, cache.contentsWeight());

        cache.checkSanity();
    }
    
    public void testSimpleAccess() throws Exception
    {
        POJOCacheElement<String,String> cache = new POJOCacheElement<String,String>(StringKeyConverter.instance,
                64, 64 * 1024, 4); // 4 == ttl in seconds
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

        
        assertEquals("[abc, 12, xxx]", cache.keysFromOldestToNewest().toString());
        assertEquals("[abc, 12, xxx]", cache.keysFromLeastToMostRecent().toString());
        
        // with access, should change
        entry = cache.findEntry(time, "12");
        assertEquals("34", entry.getValue());

        assertEquals(3, cache.size());
        // oldest/newest do not change
        assertEquals("[abc, 12, xxx]", cache.keysFromOldestToNewest().toString());
        // but most recent does
        assertEquals("[abc, xxx, 12]", cache.keysFromLeastToMostRecent().toString());

        // and even more so...
        assertEquals(3, cache.size());
        assertEquals("def", cache.findEntry(time, "abc").getValue());
        assertEquals("[abc, 12, xxx]", cache.keysFromOldestToNewest().toString());
        assertEquals("[xxx, 12, abc]", cache.keysFromLeastToMostRecent().toString());
        
        assertEquals(3, cache.size());
        assertEquals(12, cache.contentsWeight());

        cache.checkSanity();
    }

    public void testSimpleStale() throws Exception
    {
        POJOCacheElement<String,String> cache = new POJOCacheElement<String,String>(StringKeyConverter.instance,
                64, 64 * 1024, 4); // TTL 4 seconds
        assertNull(cache.putEntry(3000L, "a", "1", 5)); // stale at about 7 seconds
        assertNull(cache.putEntry(4000L, "b", "2", 3));
        assertNull(cache.putEntry(5000L, "c", "3", 1));
        assertNull(cache.putEntry(6000L, "d", "4", 9));

        assertEquals(4, cache.size());
        assertEquals(18, cache.contentsWeight());

        // at 7.5 seconds, one entry should be stale:
        assertNull(cache.findEntry(7500L, "nosuchkey"));
        assertEquals(3, cache.size());
        assertEquals(13, cache.contentsWeight());

        assertEquals("b", cache.oldestEntry(7500L).getKey());
        assertEquals("d", cache.newestEntry(7500L).getKey());
        assertEquals("d", cache.mostRecentEntry(7500L).getKey());
        assertEquals("b", cache.leastRecentEntry(7500L).getKey());

        assertEquals("[b, c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[b, c, d]", cache.keysFromLeastToMostRecent().toString());
        
        assertEquals("2", cache.findEntry(7500L, "b").getValue());
        assertEquals("[b, c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[c, d, b]", cache.keysFromLeastToMostRecent().toString());

        // at 8.5 seconds, remove one more:
        assertNull(cache.findEntry(8500L, "b"));
        assertEquals(2, cache.size());
        assertEquals(10, cache.contentsWeight());
        assertEquals("[c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[c, d]", cache.keysFromLeastToMostRecent().toString());
        assertEquals("3", cache.findEntry(8500L, "c").getValue());
        assertEquals("[c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[d, c]", cache.keysFromLeastToMostRecent().toString());

        cache.checkSanity();
    }

    public void testSimpleRemoval() throws Exception
    {
        POJOCacheElement<String,String> cache = new POJOCacheElement<String,String>(StringKeyConverter.instance,
                64, 64 * 1024, 4); // 4 == ttl in seconds
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, "a", "1", 1));
        assertNull(cache.putEntry(time, "b", "2", 2));
        assertNull(cache.putEntry(time, "c", "3", 3));
        assertNull(cache.putEntry(time, "d", "4", 4));
        assertNull(cache.putEntry(time, "e", "5", 5));
        assertEquals(5, cache.size());
        assertEquals(15, cache.contentsWeight());

        assertEquals("[a, b, c, d, e]", cache.keysFromOldestToNewest().toString());
        assertEquals("[a, b, c, d, e]", cache.keysFromLeastToMostRecent().toString());

        CacheEntry<String,String> entry = cache.removeEntry(time, "c");
        assertNotNull(entry);
        assertEquals("3", entry.getValue());
        assertEquals(4, cache.size());
        assertEquals(12, cache.contentsWeight());
        assertEquals("[a, b, d, e]", cache.keysFromOldestToNewest().toString());
        assertEquals("[a, b, d, e]", cache.keysFromLeastToMostRecent().toString());

        // dummy removals do nothing:
        assertNull(cache.removeEntry(time, "c"));
        assertNull(cache.removeEntry(time, "3"));
        assertEquals(4, cache.size());
        assertEquals(12, cache.contentsWeight());
        assertEquals("[a, b, d, e]", cache.keysFromOldestToNewest().toString());
        assertEquals("[a, b, d, e]", cache.keysFromLeastToMostRecent().toString());

        // then some more accesses... removals, find, addition
        assertEquals("2", cache.removeEntry(time, "b").getValue());
        assertEquals("5", cache.removeEntry(time, "e").getValue());
        assertEquals(2, cache.size());
        assertEquals(5, cache.contentsWeight());
        assertEquals("[a, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[a, d]", cache.keysFromLeastToMostRecent().toString());
        assertEquals("4", cache.findEntry(time, "d").getValue());
        assertEquals("[a, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[a, d]", cache.keysFromLeastToMostRecent().toString());
        assertNull(cache.findEntry(time, "b"));
        assertNull(cache.putEntry(time, "f", "6", 6));
        assertEquals(3, cache.size());
        assertEquals(11, cache.contentsWeight());
        assertEquals("[a, d, f]", cache.keysFromOldestToNewest().toString());
        assertEquals("[a, d, f]", cache.keysFromLeastToMostRecent().toString());

        cache.checkSanity();
    }

    /**
     * And then let's test a longer sequence of operations, combination of inserts, removals
     * and finds with bounded _key set (100 keys).
     */
    public void testRandomOperations()
    {
        LinkedHashMap<String,Integer> map = new LinkedHashMap<String,Integer>(100, 0.8f, true);
        POJOCacheElement<String,Integer> cache = new POJOCacheElement<String,Integer>(StringKeyConverter.instance,
                500, 64 * 1024, 4);

        Random rnd = new Random(123);
        final long time = 9000L;
        final int REP_COUNT = 999999;
        for (int i = 0; i < REP_COUNT; ++i) {
            int oper = rnd.nextInt() & 3;
            Integer arg = rnd.nextInt() & 255;
            String key = String.valueOf(arg);
            CacheEntry<String,Integer> entry = null;
            Integer resultArg, old = null;

            switch (oper) {
            case 0: // insert
                old = map.put(key, arg);
                entry = cache.putEntry(time, key, arg, 1);
                break;
            case 1: // remove
                old = map.remove(key);
                entry = cache.removeEntry(time, key);
                break;
            default: // find, twice as many as inserts/removals
                old = map.get(key);
                entry = cache.findEntry(time, key);
            }
            resultArg = (entry == null) ? null : entry.getValue();
            assertEquals("Results should have been same (oper "+oper+")", old, resultArg);

            // verify invariants
            assertEquals(map.size(), cache.size());
            assertEquals(map.size(),(int) cache.contentsWeight());
        }

        // And finally, let's ensure resulting ordering is identical
        assertEquals(map.keySet().toString(), cache.keysFromLeastToMostRecent().toString());

        cache.checkSanity();
    }
}