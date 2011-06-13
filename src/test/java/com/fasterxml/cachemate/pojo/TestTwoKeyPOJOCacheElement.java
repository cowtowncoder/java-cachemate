package com.fasterxml.cachemate.pojo;

import java.util.LinkedHashMap;
import java.util.Random;

import com.fasterxml.cachemate.*;
import com.fasterxml.cachemate.converters.IntegerKeyConverter;
import com.fasterxml.cachemate.converters.LongKeyConverter;
import com.fasterxml.cachemate.converters.StringKeyConverter;

/**
 * Unit tests to verify two-key cache element variant.
 */
public class TestTwoKeyPOJOCacheElement extends POJOTestBase
{
    /**
     * Unit test to verify that cache starts empty and with specific settings.
     */
    /**
     * Unit test to verify that cache starts empty and with specific settings.
     */
    public void testInitialState() throws Exception
    {
        // Use Strings as keys, values
        TwoKeyPOJOCacheElement<String,Long,String> cache = new TwoKeyPOJOCacheElement<String,Long,String>
            (StringKeyConverter.instance, LongKeyConverter.instance,
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
        assertNull(cache.findEntryBySecondary(time, Long.valueOf(3L)));
        assertNull(cache.findEntryBySecondary(time, Long.valueOf(-19L)));
        
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
        TwoKeyPOJOCacheElement<String,Long,String> cache = new TwoKeyPOJOCacheElement<String,Long,String>
            (StringKeyConverter.instance, LongKeyConverter.instance,
            64, 64 * 1024, /* ttl */ 4);
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, "first", Long.valueOf(15L), "yes", 3));
        assertEquals(1, cache.size());
        assertEquals(3, cache.contentsWeight());

        // basic checks after first insert
        TwoKeyCacheEntry<String,Long,String> entry = cache.oldestEntry(time);
        assertNotNull(entry);
        assertEquals("first", entry.getKey());
        assertEquals(Long.valueOf(15L), entry.getSecondaryKey());
        assertEquals("yes", entry.getValue());
        
        entry = cache.newestEntry(time);
        assertEquals("first", entry.getKey());
        entry = cache.leastRecentEntry(time);
        assertEquals("first", entry.getKey());
        entry = cache.mostRecentEntry(time);
        assertEquals("first", entry.getKey());

        // Then two more simple inserts
        assertNull(cache.putEntry(time, "12", 12L, "34", 4));
        assertNull(cache.putEntry(time, "xxx", -3L, "y", 5));
        assertEquals(3, cache.size());
        assertEquals(12, cache.contentsWeight());

        assertEquals("first", cache.oldestEntry(time).getKey());
        assertEquals(Long.valueOf(15L), cache.oldestEntry(time).getSecondaryKey());
        // no access, so this is still the same
        assertEquals("first", cache.leastRecentEntry(time).getKey());
        assertEquals("xxx", cache.newestEntry(time).getKey());
        assertEquals(Long.valueOf(-3L), cache.newestEntry(time).getSecondaryKey());
        assertEquals("xxx", cache.mostRecentEntry(time).getKey());
    }

    /**
     * Test for ensuring state remains valid when insertion leads to
     * removal due to size constraints
     */
    public void testInsertWithOverflow() throws Exception
    {
        // no time-based expiration (1000 seconds)
        TwoKeyPOJOCacheElement<String,Long,String> cache = new TwoKeyPOJOCacheElement<String,Long,String>
        (StringKeyConverter.instance, LongKeyConverter.instance,
                3, 3 * 1024, 1000L);
        // invalidate one entry per get-operation
        cache.setConfigInvalidatePerGet(1);
        
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, "abc", 1L, "def", 3));
        assertEquals(1, cache.size());
        assertEquals(3, cache.contentsWeight());
        assertNotNull(cache.findEntryBySecondary(time, Long.valueOf(1L)));
        assertNull(cache.findEntryBySecondary(time, 2L));
        ++time;
        
        assertNull(cache.putEntry(time, "bcd", 2L, "123", 4));
        assertEquals(2, cache.size());
        assertEquals(7, cache.contentsWeight());
        assertNotNull(cache.findEntryBySecondary(time, 2L));
        ++time;

        assertNull(cache.putEntry(time, "cde", 3L, "3", 5));
        assertEquals(3, cache.size());
        assertEquals(12, cache.contentsWeight());
        ++time;

        // this should result in removal of the first entry, "abc"
        assertNull(cache.putEntry(time, "def", 4L, "x", 6));
        assertEquals(3, cache.size());
        assertEquals(15, cache.contentsWeight());
        ++time;

        // this for second entry
        assertNull(cache.putEntry(time, "efg", 5L, "x", 2));
        assertEquals(3, cache.size());
        assertEquals(13, cache.contentsWeight());
        ++time;

        // and this for the third original entry
        assertNull(cache.putEntry(time, "fgh", 6L, "x", 1));
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
        assertNull(cache.putEntry(time, "xxx", 7L, "yyy", 9));
        assertEquals(1, cache.size());
        assertEquals(9, cache.contentsWeight());

        assertNull(cache.findEntry(time, "abc"));
        // should not find initial insert by secondary either
        assertNull(cache.findEntryBySecondary(time, 1L));    
    }

    public void testSimpleAccess() throws Exception
    {
        TwoKeyPOJOCacheElement<String,Long,String> cache = new TwoKeyPOJOCacheElement<String,Long,String>
            (StringKeyConverter.instance, LongKeyConverter.instance,
                64, 64 * 1024, 4); // 4 == ttl in seconds
        long time = 3000L; // at "3 seconds"

        assertNull(cache.putEntry(time, "abc", 1L, "def", 3));

        time += 1000L; // 4 seconds
        assertNull(cache.putEntry(time, "12", 2L, "34", 4));
        time += 1000L; // 5 seconds
        assertNull(cache.putEntry(time, "xxx", 3L, "y", 5));

        // Starting state:
        TwoKeyCacheEntry<String,Long,String> entry = cache.oldestEntry(time);
        assertNotNull(entry);
        assertEquals("abc", entry.getKey());
        assertEquals(Long.valueOf(1L), entry.getSecondaryKey());
        assertEquals("abc", cache.leastRecentEntry(time).getKey());
        assertEquals("xxx", cache.newestEntry(time).getKey());
        assertEquals(Long.valueOf(3L), cache.newestEntry(time).getSecondaryKey());
        assertEquals("xxx", cache.mostRecentEntry(time).getKey());

        
        assertEquals("[abc, 12, xxx]", cache.keysFromOldestToNewest().toString());
        assertEquals("[abc, 12, xxx]", cache.keysFromLeastToMostRecent().toString());
        
        // with access, should change
        entry = cache.findEntryBySecondary(time, Long.valueOf(2L));
        assertEquals("34", entry.getValue());

        assertEquals(3, cache.size());
        // oldest/newest do not change
        assertEquals("[abc, 12, xxx]", cache.keysFromOldestToNewest().toString());
        // but most recent does
        assertEquals("[abc, xxx, 12]", cache.keysFromLeastToMostRecent().toString());

        // and even more so...
        assertEquals(3, cache.size());
        assertEquals("def", cache.findEntryBySecondary(time, Long.valueOf(1L)).getValue());
        assertEquals("[abc, 12, xxx]", cache.keysFromOldestToNewest().toString());
        assertEquals("[xxx, 12, abc]", cache.keysFromLeastToMostRecent().toString());
        
        assertEquals(3, cache.size());
        assertEquals(12, cache.contentsWeight());
    }

    public void testSimpleStale() throws Exception
    {
        TwoKeyPOJOCacheElement<String,Long,String> cache = new TwoKeyPOJOCacheElement<String,Long,String>
        (StringKeyConverter.instance, LongKeyConverter.instance,
                64, 64 * 1024, 4); // TTL 4 seconds
        assertNull(cache.putEntry(3000L, "a", 1L, "1", 5)); // stale at about 7 seconds
        assertNull(cache.putEntry(4000L, "b", 2L, "2", 3));
        assertNull(cache.putEntry(5000L, "c", 3L, "3", 1));
        assertNull(cache.putEntry(6000L, "d", 4L, "4", 9));

        assertEquals(4, cache.size());
        assertEquals(18, cache.contentsWeight());

        // at 7.5 seconds, one entry should be stale:
        assertNull(cache.findEntry(7500L, "nosuchkey"));
        assertEquals(3, cache.size());
        assertEquals(13, cache.contentsWeight());

        assertEquals(Long.valueOf(2L), cache.oldestEntry(7500L).getSecondaryKey());
        assertEquals(Long.valueOf(4L), cache.newestEntry(7500L).getSecondaryKey());
        assertEquals(Long.valueOf(4L), cache.mostRecentEntry(7500L).getSecondaryKey());
        assertEquals(Long.valueOf(2L), cache.leastRecentEntry(7500L).getSecondaryKey());

        assertEquals("[b, c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[b, c, d]", cache.keysFromLeastToMostRecent().toString());
        
        assertEquals("2", cache.findEntryBySecondary(7500L, Long.valueOf(2L)).getValue());
        assertEquals("[b, c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[c, d, b]", cache.keysFromLeastToMostRecent().toString());

        // at 8.5 seconds, remove one more:
        assertNull(cache.findEntryBySecondary(8500L, Long.valueOf(2L)));
        assertEquals(2, cache.size());
        assertEquals(10, cache.contentsWeight());
        assertEquals("[c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[c, d]", cache.keysFromLeastToMostRecent().toString());
        assertEquals("3", cache.findEntryBySecondary(8500L, Long.valueOf(3L)).getValue());
        assertEquals("[c, d]", cache.keysFromOldestToNewest().toString());
        assertEquals("[d, c]", cache.keysFromLeastToMostRecent().toString());
    }

    public void testSimpleRemoval() throws Exception
    {
        TwoKeyPOJOCacheElement<Integer,String,String> cache = new TwoKeyPOJOCacheElement<Integer,String,String>
            (IntegerKeyConverter.instance, StringKeyConverter.instance,
                64, 64 * 1024, 4); // 4 == ttl in seconds
        long time = 3000L; // at "3 seconds"
        assertNull(cache.putEntry(time, 1, "a", "1", 1));
        assertNull(cache.putEntry(time, 2, "b", "2", 2));
        assertNull(cache.putEntry(time, 3, "c", "3", 3));
        assertNull(cache.putEntry(time, 4, "d", "4", 4));
        assertNull(cache.putEntry(time, 5, "e", "5", 5));
        assertEquals(5, cache.size());
        assertEquals(15, cache.contentsWeight());

        assertEquals("[1, 2, 3, 4, 5]", cache.keysFromOldestToNewest().toString());
        assertEquals("[1, 2, 3, 4, 5]", cache.keysFromLeastToMostRecent().toString());

        TwoKeyCacheEntry<Integer,String,String> entry = cache.removeEntry(time, 3);
        assertNotNull(entry);
        assertEquals("3", entry.getValue());
        assertEquals(4, cache.size());
        assertEquals(12, cache.contentsWeight());
        assertEquals("[1, 2, 4, 5]", cache.keysFromOldestToNewest().toString());
        assertEquals("[1, 2, 4, 5]", cache.keysFromLeastToMostRecent().toString());

        // dummy removals do nothing:
        assertNull(cache.removeEntry(time, 3));
        assertNull(cache.removeEntry(time, 189));
        assertEquals(4, cache.size());
        assertEquals(12, cache.contentsWeight());
        assertEquals("[1, 2, 4, 5]", cache.keysFromOldestToNewest().toString());
        assertEquals("[1, 2, 4, 5]", cache.keysFromLeastToMostRecent().toString());

        // then some more accesses... removals, find, addition
        assertEquals("2", cache.removeEntry(time, 2).getValue());
        assertEquals("5", cache.removeEntry(time, 5).getValue());
        assertEquals(2, cache.size());
        assertEquals(5, cache.contentsWeight());
        assertEquals("[1, 4]", cache.keysFromOldestToNewest().toString());
        assertEquals("[1, 4]", cache.keysFromLeastToMostRecent().toString());
        assertEquals("4", cache.findEntryBySecondary(time, "d").getValue());
        assertEquals("[1, 4]", cache.keysFromOldestToNewest().toString());
        assertEquals("[1, 4]", cache.keysFromLeastToMostRecent().toString());
        assertNull(cache.findEntryBySecondary(time, "b"));
        assertNull(cache.putEntry(time, 6, "f", "6", 6));
        assertEquals(3, cache.size());
        assertEquals(11, cache.contentsWeight());
        assertEquals("[1, 4, 6]", cache.keysFromOldestToNewest().toString());
        assertEquals("[1, 4, 6]", cache.keysFromLeastToMostRecent().toString());
    }

    /**
     * And then let's test a longer sequence of operations, combination of inserts, removals
     * and finds with bounded _key set (100 keys).
     */
    public void testRandomOperations()
    {
        LinkedHashMap<String,Integer> map = new LinkedHashMap<String,Integer>(100, 0.8f, true);
        TwoKeyPOJOCacheElement<String,Integer,Integer> cache = new TwoKeyPOJOCacheElement<String,Integer,Integer>
            (StringKeyConverter.instance, IntegerKeyConverter.instance,
                500, 64 * 1024, 4);

        Random rnd = new Random(123);
        final long time = 9000L;
        final int REP_COUNT = 999999;
        for (int i = 0; i < REP_COUNT; ++i) {
            int oper = rnd.nextInt() & 3;
            Integer arg = rnd.nextInt() & 255;
            String key = String.valueOf(arg);
            Integer key2 = arg;
            TwoKeyCacheEntry<String,Integer,Integer> entry = null;
            Integer resultArg, old = null;

            switch (oper) {
            case 0: // insert
                old = map.put(key, arg);
                entry = cache.putEntry(time, key, key2, arg, 1);
                break;
            case 1: // remove
                old = map.remove(key);
                entry = cache.removeEntry(time, key);
                break;
            default: // find, twice as many as inserts/removals
                old = map.get(key);
                // look up by secondary key (important!)
                entry = cache.findEntryBySecondary(time, key2);
            }
            resultArg = (entry == null) ? null : entry.getValue();
            assertEquals("Results should have been same (oper "+oper+")", old, resultArg);

            // verify invariants
            assertEquals(map.size(), cache.size());
            assertEquals(map.size(),(int) cache.contentsWeight());
        }

        // And finally, let's ensure resulting ordering is identical
        assertEquals(map.keySet().toString(), cache.keysFromLeastToMostRecent().toString());
    }}
