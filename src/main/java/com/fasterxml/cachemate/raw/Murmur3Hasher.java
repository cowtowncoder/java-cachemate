package com.fasterxml.cachemate.raw;

/**
 * {@link Hasher} implementation that uses fast and well hashing
 * Murmur3 algorithm (see [http://en.wikipedia.org/wiki/MurmurHash])
 * and specifically its 32-bit variant.
 */
public class Murmur3Hasher extends Hasher
{
    private final static int c1 = 0xcc9e2d51;
    private final static int c2 = 0x1b873593;
        
    private final static int c3 = 0xe6546b64;

    private final static int DEFAULT_SEED = 0;
    
    @Override
    public int calcHash(byte[] data, int offset, int len)
    {
        int h1 = DEFAULT_SEED;
        int roundedEnd = offset + (len & 0xfffffffc);  // round down to 4 byte block

        for (int i=offset; i<roundedEnd; i+=4) {
                int k1 = (data[i] & 0xff) | ((data[i+1] & 0xff) << 8) | ((data[i+2] & 0xff) << 16) | (data[i+3] << 24);
                k1 *= c1;
                k1 = (k1 << 15) | (k1 >>> 17);  // ROTL32(k1,15);
                k1 *= c2;
                h1 ^= k1;
                h1 = (h1 << 13) | (h1 >>> 19);  // ROTL32(h1,13);
                h1 = h1 * 5 + c3;
        }

        int k1 = 0;
        switch(len & 0x03) {
              case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
              case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
              case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = (k1 << 15) | (k1 >>> 17);  // ROTL32(k1,15);
                k1 *= c2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= len;

        // fmix(h1);
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }
}
