package com.fasterxml.cachemate.util;

public class TimeUtil
{
    /**
     * Constants we use to check whether conversion to "quarters" can be
     * done using just ints
     */
    private final static int MAX_SECONDS_FOR_INT = Integer.MAX_VALUE / 1000;
	
    /**
     * Helper method that converts from seconds into internal time unit
     * (which is approximately "quarter of a second")
     */
    public static int secondsToInternal(int seconds)
    {
        // fastest way is without converting to long, can be used for most cases:
        if (seconds < MAX_SECONDS_FOR_INT) {
            return (seconds * 1000) >>> 8;
        }
        // if not, use long
        long msecs = 1000L * seconds;
        return (int) (msecs >>> 8);
    }
    
    public final static int timeToTimestamp(long currentTimeMsecs)
    {
        int time = (int) (currentTimeMsecs >> 8); // divide by 256, to quarter-seconds
        if (time < 0) {
            time &= 0x7FFFFFFF;
        }
        return time;
    }

}
