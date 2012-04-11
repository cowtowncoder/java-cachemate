# Overview

CacheMate is a multi-level, in-memory, in-process (JVM) cache, with optional secondary key access.

CacheMate provides two main cache implementations (called "cache elements"): Object-based LRU-based heap cache and raw byte-based off-heap cache; former being fully implemented and latter work in progress.

In addition to providing configurable individual cache elements, CacheMate aims to provide out-of-box composite caches that use individual elements as layers to provide multi-level hierarchic cache, combining best features of on-heap POJO and off-heap raw caches.

## Documentation

Check out [project wiki](https://github.com/cowtowncoder/java-cachemate/wiki) for more documentation, including javadocs

## Status

On-heap POJO cache element implementation is complete, well-tested and used in production system. It can be thought of as a more memory-efficient (and time-bound, with TTL) version of JDK's java.util.LinkedHashMap.

Secondary cache element, "raw" off-heap byte array cache is being actively developed and should be in usable state by May 2012. It will sport following features:

 * Strict memory-use bounds, defined by application
 * Design that allows efficient multi-threaded access (multi-threaded access is considered an important goal)
 * Append-only insert, which balances good read performance with very good write performance
 * Efficient time-bound eviction, by discarding blocks instead of individual entries; in general, more FIFO style than LRU

Beyond implementing basic cache elements, the plan is to allow these components to be usable both via "glue" abstractions that build multi-level logical cache, and as separate stand-alone components (in case more custom interaction is needed).

## Usage, on-heap POJO cache

### Simple one-key cache element

For simple usage of the first-level "POJO" cache, you can do:

    // First, construct "Cache element" (single-level cache) for
    // simple MyPojos by String cache:
    int maxEntries = 500; // at most 500 instances
    long maxMemory = 5 * 1000 * 1000; // and at most 5 million bytes (if we can estimate)
    int timeToLiveSecs = 5 * 60; // maximum time-to-live, 5 minutes (300 seconds)
    CacheElement<String,MyPojo> cache = new POJOCacheElement<String,MyPojo>
      (StringKeyConverter.instance, maxEntries, maxMemory, timeToLiveSecs);

    // then add entries:
    String key = "first";
    MyPojo value = ....; // however you get it
    int memUsage = ... ; // likewise, you must provide this -- if N/A, just use 1
    cache.putEntry(System.currentTimeMillis(), key, value, memUsage);

    // and/or access
    CacheEntry<String,MyPojo> cached = cache.findEntry(System.currentTimeMillis(), "first");
    if (cached != null) { // we found it!
      // use cached...
    } else {
      // or fetch, then usually add
    }

    // or invalidate
    cache.removeEntry(System.currentTimeMillis(), "first");

### Two-key cache element

If you want to use secondary keys, access is quite similar, you just need to provide secondary keys if you want; note that primary key access is still same as above:

    // Let's assume there is primary numeric id, and secondary name
    TwoKeyCacheElement<Long,String,MyPojo> cache2 = new TwoKeyPOJOCacheElement<Long,String,MyPojo>
      (LongKeyConverter.instance, StringKeyConverter.instance, maxEntries, maxMemory, timeToLiveSecs);

    // if using single-key methods, assumes no secondary key associated with entry:
    Long primaryKey = 1234L;
    String secondaryKey = "Sponge Bob";
    cache2.putEntry(System.currentTimeMillis(), primaryKey, secondarKey, value, memUsage);

    // can then access either by primary, as before
    TwoKeyCacheEntry<Long,String,MyPojo> cached2 = cache2.findEntry(System.currentTimeMillis(), "first");
    // or using secondary key
    cached2 = cache2.findEntryBySecondary(System.currentTimeMillis(), secondaryKey);
    // note, however, that removal is ALWAYS by primary key -- so if you want
    // to remove by secondary key, do:
    
    TwoKeyCacheEntry<Long,String,MyPojo> entryToRemove = cache2.findEntryBySecondary(System.currentTimeMillis(), secondaryKey);
    if (entryToRemove != null) {
        String secondaryKey = entryToRemove.getSecondaryKey();
        if (secondaryKey != null) {
            cache.removeEntry(System.currentTimeMillis(), entryToRemove.
        }
    }

## Even more keys?

Currently there is only support for two-key variants, but it is easy to extend this if necessary: new interfaces and implementations are needed but pattern is easy.
The main reason for not yet adding is that since API is still evolving, it will be less working adding higher-dimension variants lazily as needed.

## Usage, off-heap "raw" cache

Since this cache element is still being implemented, no usage available yet!
