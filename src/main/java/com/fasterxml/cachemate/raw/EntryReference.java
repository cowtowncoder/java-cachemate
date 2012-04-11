package com.fasterxml.cachemate.raw;

import java.nio.ByteBuffer;

/**
 * Each reference points to a chunk of bytes structured as follows:
 *<pre>
 * Timestamp (4-bytes): valid until
 * VInt keyLength K
 * K bytes of key
 * (for multi-key entries, more VInt/byte[] pairs)
 * VInt valueLength V
 * V bytes of value
 *</pre>
 */
public class EntryReference
{
	/**
	 * Underlying buffer in which entry is stored
	 */
	protected final ByteBuffer _buffer;

	protected final int _startOffset;

	protected final int _timestamp;
	
	protected final int _keyOffset;
	protected final int _keyLength;
	
	public EntryReference(ByteBuffer buf, int start)
	{
		_buffer = buf;
		_startOffset = start;
		buf.position(start);
		// decode basic info eagerly
		_timestamp = buf.getInt();
		_keyLength = _readVInt(buf);
		_keyOffset = buf.position();
	}

    /*
    /**********************************************************************
    /* Public API
    /**********************************************************************
     */

	public boolean hasKey(byte[] key)
	{
		if (key.length != _keyLength) {
			return false;
		}
		final ByteBuffer buf = _buffer;
		buf.position(_keyOffset);
		for (int i = 0, end = _keyLength; i < end; ++i) {
			if (buf.get() != key[i]) {
				return false;
			}
		}
		return true;
	}
	
    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

	private final int _readVInt(ByteBuffer bbuf)
	{
		int value = bbuf.get();
		// short-cut for common case:
		if (value >= 0) {
			return value;
		}
		value = value & 0x7F;

		int b;
		while ((b = bbuf.get()) < 0) {
			value = (value << 7) | (b & 0x7F);
		}
		value = (value << 7) | b;
		return value;
	}
}
