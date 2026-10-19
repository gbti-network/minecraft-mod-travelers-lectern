package com.gbti.travelerslectern;

/**
 * Lengths of time in words, for the action bar and the lectern manager: seconds under a minute,
 * minutes under two hours, hours under two days, then days. A wait is rounded up, so it never reads
 * "0 minutes" while there is still time left; an age is rounded down.
 */
public final class Durations {
    private Durations() {
    }

    /** How long is left, rounded up: "26 minutes". */
    public static String left(long seconds) {
        return words(Math.max(1, seconds), true);
    }

    /** How long ago, rounded down: "4 minutes". */
    public static String ago(long seconds) {
        return words(Math.max(0, seconds), false);
    }

    /** A cooldown as set: "30 minutes", "45 seconds". */
    public static String of(long seconds) {
        return words(Math.max(0, seconds), true);
    }

    private static String words(long seconds, boolean up) {
        if (seconds < 60) return count(seconds, "second");
        if (seconds < 2 * 3600) return count(divide(seconds, 60, up), "minute");
        if (seconds < 2 * 86400) return count(divide(seconds, 3600, up), "hour");
        return count(divide(seconds, 86400, up), "day");
    }

    private static long divide(long value, long unit, boolean up) {
        return up ? (value + unit - 1) / unit : value / unit;
    }

    private static String count(long n, String unit) {
        return n == 1 ? "1 " + unit : n + " " + unit + "s";
    }
}
