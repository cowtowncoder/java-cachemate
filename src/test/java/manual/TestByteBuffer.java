package manual;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import sun.misc.Unsafe;

public class TestByteBuffer 
{
    public final static int BUF_SIZE = 32 * (1 << 20); // 8 megs

    private static final Unsafe unsafe;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final long BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);

    private final Field addressField;
    
    public TestByteBuffer() throws Exception
    {
        addressField = Buffer.class.getDeclaredField("address");
        if (addressField == null) {
            throw new IllegalStateException();
        }
        addressField.setAccessible(true);
    }

    public void test() throws Exception
    {
        ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE);
        
        byte[] bytes = new byte[BUF_SIZE];

        System.out.println("Direct buffer, type: "+buf.getClass().getName());
        
        buf.put(0, (byte) 0x7f);
        int round = 0;
        
        while (true) {
            long now = System.currentTimeMillis();
            String msg;
            switch (round) {
            case 0:
                testWithBulk(buf, bytes);
                msg = "with BULK";
                break;
            case 1:
                testManual(buf, bytes);
                msg = "manual";
                break;
            case 2:
                testUnsafe(buf, bytes);
                msg = "Unsafe";
                break;
            default:
                throw new Error();
            }
            long time = System.currentTimeMillis() - now;
            // just to ensure no dead code optimization occurs...
            byte first = bytes[0];
            System.out.println("Test: "+msg+" took "+time+" msecs (byte: "+first+")");
            round = (round + 1) % 3;
            if (round == 0) {
                System.out.println();
            }
            Thread.sleep(100L);
        }
    }
    
    private final void testWithBulk(ByteBuffer bbuf, byte[] bytes)
    {
        bbuf.position(0);
        bbuf.get(bytes, 0, bytes.length);
    }
    
    private final void testManual(ByteBuffer bbuf, byte[] bytes)
    {
        for (int i = 0, end = bytes.length; i < end; ++i) {
            bytes[i] = bbuf.get(i);
        }
    }

    private final void testUnsafe(ByteBuffer bbuf, byte[] bytes) throws IllegalAccessException
    {
        long src = addressField.getLong(bbuf);
        unsafe.copyMemory(null, src, bytes, BYTE_ARRAY_OFFSET, bytes.length);
    }
    
    public static void main(String[] args) throws Exception
    {
        new TestByteBuffer().test();
    }
}
