package com.example.tomodoom.stats;

import androidx.annotation.NonNull;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Local calendar days as plain numbers ("epoch days" in a time zone), so the stats can bucket by
 * day without {@code java.time} (not available on minSdk 24).
 */
public final class LocalDays {

    public static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

    private LocalDays() {
    }

    /** @return the local day number that {@code timestampMillis} falls on in {@code zone}. */
    public static long dayOf(long timestampMillis, @NonNull TimeZone zone) {
        return Math.floorDiv(timestampMillis + zone.getOffset(timestampMillis), DAY_MILLIS);
    }

    /** @return the instant local midnight starts {@code day} in {@code zone}. */
    public static long startOf(long day, @NonNull TimeZone zone) {
        long utcMidnight = day * DAY_MILLIS;
        // The offset at local midnight; using the offset a few hours earlier keeps DST days right.
        return utcMidnight - zone.getOffset(utcMidnight - zone.getRawOffset());
    }
}
