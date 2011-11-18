CacheMate is a simple in-memory, in-process (JVM) cache.
It uses multiple (by default, 3) level cache, and simple LRU-based generational promote/demote algorithm.

# Documentation

Check out [project wiki](https://github.com/cowtowncoder/java-cachemate/wiki) for more documentation, including javadocs

# Status

First part of the library is complete, well-tested and used in production system -- this is the in-memory, "level 1" cache.
It can be thought of as a more memory-efficient (and time-bound, with TTL) version of JDK's java.util.LinkedHashMap.

Secondary/tertiary cache components will be the more interesting part, as they will support strict memory usage limits (based on serialized data); primary using non-heap memory (ByteBuffer), but possibly on-disk (SSD) as well.
These components have not yet been implemented, but many of existing first-level abstractions will be used as-is.

The general plan is to allow these components to be usable both via "glue" abstractions that build multi-level logical cache, and as separate stand-alone components (in case more custom interaction is needed).
