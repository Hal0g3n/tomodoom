package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A detected doom-scroll session, reported by a {@code DoomScrollMonitor} to its
 * {@code DoomScrollListener}.
 *
 * <p>The monitor decides how severe the session was via {@link #getHpPenalty()}; the
 * {@code DoomScrollHpBridge} applies it to the pet.</p>
 */
public final class DoomScrollEvent {

    /** Suggested penalty for a single detected session. */
    public static final int DEFAULT_HP_PENALTY = 5;

    @Nullable
    private final String sourcePackage;
    private final long durationMillis;
    private final int hpPenalty;
    private final long timestampMillis;

    /**
     * @param sourcePackage   package name of the app being doom-scrolled, or {@code null} if unknown.
     * @param durationMillis  length of the detected session (≥ 0).
     * @param hpPenalty       HP to remove (≥ 0; negative values are treated as 0).
     * @param timestampMillis wall-clock time the session was detected.
     */
    public DoomScrollEvent(@Nullable String sourcePackage,
                           long durationMillis,
                           int hpPenalty,
                           long timestampMillis) {
        this.sourcePackage = sourcePackage;
        this.durationMillis = Math.max(0L, durationMillis);
        this.hpPenalty = Math.max(0, hpPenalty);
        this.timestampMillis = timestampMillis;
    }

    /** Convenience factory stamped with the current time. */
    @NonNull
    public static DoomScrollEvent now(@Nullable String sourcePackage, long durationMillis, int hpPenalty) {
        return new DoomScrollEvent(sourcePackage, durationMillis, hpPenalty, System.currentTimeMillis());
    }

    @Nullable
    public String getSourcePackage() {
        return sourcePackage;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public int getHpPenalty() {
        return hpPenalty;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @NonNull
    @Override
    public String toString() {
        return "DoomScrollEvent{pkg=" + sourcePackage + ", durationMs=" + durationMillis
                + ", penalty=" + hpPenalty + "}";
    }
}
