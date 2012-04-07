package manual;

import java.util.Arrays;
import java.util.Random;

/* Manually run piece of code that calculates branching factors
 * of a 4-level 32-bit trie, assuming uniformly distributed keys
 */
public class TestDistribution
{
    public static void main(String[] args)
    {
        if (args.length != 1) {
            System.err.println("Usage: java ... [size]");
            System.exit(1);
        }
        int count = Integer.parseInt(args[0]);
        int[] keys = new int[count];
        Random rnd = new Random(count);
        for (int i = 0; i < count; ++i) {
            keys[i] = rnd.nextInt();
        }
        Arrays.sort(keys);
        
        int first = 1;
        int second = 1;
        int third = 1;
        // no fourth, since that would be same as count

        int prev = keys[0];
        for (int i = 1; i < count; ++i) {
            int curr = keys[i];
            if ((prev >> 24) != (curr >> 24)) {
                ++first;
            }
            if ((prev >> 16) != (curr >> 16)) {
                ++second;
            }
            if ((prev >> 8) != (curr >> 8)) {
                ++third;
            }
            prev = curr;
        }
        System.out.printf("Done: %d keys -> first level: %d, second: %d, third: %d\n",
                count, first, second, third);
    }
}
