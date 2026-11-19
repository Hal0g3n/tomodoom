package com.example.tomodoom.repository;

/**
 * How much HP the pet loses while the user stays out of the app. Pure Java, so it is unit-tested
 * on the JVM.
 *
 * <p>After {@link #GRACE_MILLIS} away, HP decays <b>in proportion to what is left</b>:
 * {@code hpAfter = round(hp × RETAINED_PER_PERIOD ^ (t / PERIOD_MILLIS))}, where {@code t} is the
 * time away minus the grace period. From 100 HP that is about 70 after 8.5 h, 34 after a day,
 * 12 after two days and 0 after a week, so a long absence flattens out instead of growing
 * linearly.</p>
 *
 * <p>The curve is memoryless: checking in every few hours costs the same as staying away (and each
 * visit earns a fresh grace period), so coming back is never worse than not coming back.</p>
 */
public final class InactivityDecayPolicy {

    /** No decay for the first 30 minutes away. */
    public static final long GRACE_MILLIS = 30L * 60L * 1000L;
    /** Every {@link #PERIOD_MILLIS} after the grace period, the pet keeps this fraction of its HP. */
    public static final double RETAINED_PER_PERIOD = 0.7;
    /** 8 hours. */
    public static final long PERIOD_MILLIS = 8L * 60L * 60L * 1000L;

    private InactivityDecayPolicy() {
    }

    /**
     * @param currentHp  the pet's HP now (values ≤ 0 lose nothing).
     * @param awayMillis how long the app was in the background; negative (the clock went back)
     *                   loses nothing.
     * @return the HP to remove, between 0 and {@code currentHp}.
     */
    public static int hpLoss(int currentHp, long awayMillis) {
        if (currentHp <= 0 || awayMillis <= GRACE_MILLIS) {
            return 0;
        }
        double periods = (double) (awayMillis - GRACE_MILLIS) / PERIOD_MILLIS;
        long remaining = Math.round(currentHp * Math.pow(RETAINED_PER_PERIOD, periods));
        if (remaining < 0) {
            remaining = 0;
        }
        return (int) Math.min(currentHp, currentHp - remaining);
    }
}
