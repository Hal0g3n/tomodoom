package com.example.tomodoom.stats;

import java.util.concurrent.TimeUnit;

/**
 * Splits a duration into the two largest units the Stats page shows ("3 d 4 h", "2 h 15 min",
 * "12 min"). Pure Java; the UI turns it into localised text.
 */
public final class DurationSplit {

    public enum Unit {
        /** Under a minute. */
        UNDER_MINUTE,
        /** {@link #getMajor()} minutes. */
        MINUTES,
        /** {@link #getMajor()} hours and {@link #getMinor()} minutes. */
        HOURS,
        /** {@link #getMajor()} days and {@link #getMinor()} hours. */
        DAYS
    }

    private final Unit unit;
    private final long major;
    private final long minor;

    private DurationSplit(Unit unit, long major, long minor) {
        this.unit = unit;
        this.major = major;
        this.minor = minor;
    }

    /** @param millis a duration; negative counts as 0. Rounds down. */
    public static DurationSplit of(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0, millis));
        if (minutes < 1) {
            return new DurationSplit(Unit.UNDER_MINUTE, 0, 0);
        }
        if (minutes < 60) {
            return new DurationSplit(Unit.MINUTES, minutes, 0);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return new DurationSplit(Unit.HOURS, hours, minutes % 60);
        }
        return new DurationSplit(Unit.DAYS, hours / 24, hours % 24);
    }

    public Unit getUnit() {
        return unit;
    }

    public long getMajor() {
        return major;
    }

    public long getMinor() {
        return minor;
    }
}
