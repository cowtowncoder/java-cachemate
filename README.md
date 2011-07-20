CacheMate is a simple in-memory, in-process (JVM) cache.
It uses multiple (by default, 3) level cache, and simple LRU-based generational promote/demote algorithm.

# Status

First part of the library is complete, well-tested and used in production system -- this is the in-memory, "level 1" cache. It can be thought of as a more memory-efficient (and time-bound) version of JDK's java.util.LinkedHashMap.

Secondary/tertiary levels (in-memory "raw"; possibly disk-backed raw storage) have not yet been implemented; the plan is to implement these as similar elements, and offer higher-level constructs that act as glue to connect more than level into functional multi-level logical cache.
There levels will enforce strict limits based on size estimates from serialized objects (i.e. byte arrays), as well as TTL, and use non-heap storage (ByteBuffer).

As a general policy. all pieces should also be usable stand-alone without higher-level "glue" abstractions.
