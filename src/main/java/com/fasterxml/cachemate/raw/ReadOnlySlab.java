package com.fasterxml.cachemate.raw;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lowest level "raw" storage entity, backed by a slice of a physical
 * {@link ByteBuffer}, and stored in "frozen" structure meaning
 * that reads should be fast but no modifications may be done.
 *<p>
 * Each slab consists of two adjacent areas: first is the entry area,
 * where entries are laid sequentially; and second is the index area
 * that contains hashes and references to entry area.
 *<p>
 * Each entry consists of following byte sequence:
 * <ul>
 *  <li>4-byte timestamp: valid until
 *  <li>VInt keyLength K
 *  <li>K bytes of key
 *  <li> (for multi-key entries, more VInt/byte[] pairs)
 *  <li>VInt valueLength V
 *  <li>V bytes of value
 *  </ul>
 *<p>
 * Index area consists of two areas with same number of entries (let's
 * call that 'I'): first one contains set of I ints (4 bytes), consisting
 * of 32-bit hash, ordered for binary search; and second matching set with
 * offsets for that hash to an entry in entry area.
 */
public final class ReadOnlySlab
{
    /*
    /**********************************************************************
    /* Config, data
    /**********************************************************************
     */

    // note: we do NOT hold on to a ByteBuffer, since copies are needed
	
    /**
     * Absolute offset within shared {@link ByteBuffer} where this slab starts.
     * This is also where the entry (data) area starts.
     */
    protected final int _slabStartOffset;

    /**
     * Absolute offset within shared {@link ByteBuffer} that points to the
     * offset right after last byte that is part of this slab (that is,
     * exclusive end). It is also the end offset of the index area.
     */
    protected final int _slabEndOffset;

    /**
     * Absolute offset within shared {@link ByteBuffer} that points to the
     * start of index area.
     * It is also the end offset of the entry area.
     */
    protected final int _indexStartOffset;
	
    /**
     * Number of entries contained in the index. Size of the index area is
     * 8 times this (2 ints, hash, entry offset).
     */
    protected final int _entryCount;

    /**
     * Flag to indicate that there is at least one hash collision in this
     * slab; since this should be uncommon occurence, knowledge can be used
     * for simplifying lookups in cases there are no collisions.
     */
    protected final boolean _hashCollisions;

    /*
    /**********************************************************************
    /* Config, other
    /**********************************************************************
     */

    /**
     * Reference to the next slab in the logical linked list; will
     * be cleared to null when slabs expire.
     */
    protected final AtomicReference<ReadOnlySlab> _nextSlab = new AtomicReference<ReadOnlySlab>();
	
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    public ReadOnlySlab(int slabStart, int slabEnd,
            int indexStart, int entryCount,
            boolean hashCollisions)
    {
        _slabStartOffset = slabStart;
        _slabEndOffset = slabEnd;
        _indexStartOffset = indexStart;
        _entryCount = entryCount;
        _hashCollisions = hashCollisions;
    }
	
    /*
    /**********************************************************************
    /* Public API
    /**********************************************************************
     */

    /**
     * @param key Primary key of the entry to find
     * @param keyHash Full hash code of the entry
     * 
     * @return Entry with specified primary key, if any contained; null if not
     */
    public EntryReference findEntry(ByteBuffer bbuf, byte[] key, int keyHash)
    {
        int min = 0;
        int max = _entryCount-1;
        int offset;
        int mid;

        // First, let's see if we do find a matching hash
        // (let's see -- can I still code binary search?)
        while (true) {
            mid = (min + max) >> 1;
            offset = _indexStartOffset + (mid << 2);
            int currHash = bbuf.getInt(offset);
            if (currHash > keyHash) { // key is in lower subset
                max = mid-1;
            } else if (currHash != keyHash) { // upper
                min = mid+1;
            } else {
                break;
            }
            if (max > min) { // no match!
                return null;
            }
        }

        // if we have collisions, need more delicate handling, so:
        if (_hashCollisions)  {
            return _findEntryWithDups(bbuf, key, keyHash, mid);
        }
        // otherwise we simply need to verify that key matches
        int entryOffset = bbuf.getInt(offset + (_entryCount << 2));
        EntryReference ref = new EntryReference(bbuf, entryOffset);
        return ref.hasKey(key) ? ref : null;
    }
	
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    /**
     * Helper method called to find the match, in case where at least two
     * entries have same hash value (collision). If so, we are still guaranteed
     * that all same-hash-valued entries are adjacent; but we need to scan through
     * them all.
     */
    private final EntryReference _findEntryWithDups(ByteBuffer bbuf,
            byte[] key, int keyHash, final int matchIndex)
    {      
        // assume entries are ordered from most to least recent; hence, find first one first:
        int ix = matchIndex;

        while (--ix > 0 && bbuf.getInt(_indexStartOffset + (ix << 2)) == keyHash) {
            ;
        }
        ++ix; // to compensate for the last speculative reduction

        // and see if we can find a match...
        while (true) {
            int entryOffset = bbuf.getInt(_indexStartOffset + (ix << 2) + (_entryCount << 2));
            EntryReference ref = new EntryReference(bbuf, entryOffset);
            if (ref.hasKey(key)) {
                return ref;
            }
            // if not, does the next entry have same hash code still?
            if (++ix >=_entryCount || bbuf.getInt(_indexStartOffset + (ix << 2)) != keyHash) {
                return null;
            }
        }
    }
}
