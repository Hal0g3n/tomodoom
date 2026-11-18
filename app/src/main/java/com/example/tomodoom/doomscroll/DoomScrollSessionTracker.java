package com.example.tomodoom.doomscroll;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure-Java session rules for doom-scroll detection.
 *
 * <p>Only time seen live between two polls counts, and only while a watched app is in the
 * foreground and {@code countable} is true (portrait, screen on). Replayed usage history and long
 * gaps between polls are never charged. Landscape pauses a session. Being away from watched apps
 * for the grace period ends it, so in-app screen changes and app-hopping don't reset the timer.</p>
 *
 * <p>Not thread-safe: the monitor calls it while holding its lock.</p>
 */
final class DoomScrollSessionTracker {

    private static final long NONE = -1L;

    /** What became due during one poll. */
    static final class Tick {
        @NonNull
        final String sourcePackage;
        /** Whole penalty intervals newly completed; 0 if none. */
        final int penaltyIntervals;
        /** Counted session length this warning marks; 0 if no warning is due. */
        final long warningSessionMillis;

        Tick(@NonNull String sourcePackage, int penaltyIntervals, long warningSessionMillis) {
            this.sourcePackage = sourcePackage;
            this.penaltyIntervals = penaltyIntervals;
            this.warningSessionMillis = warningSessionMillis;
        }
    }

    @NonNull
    private final Set<String> monitoredPackages;
    private final long penaltyIntervalMillis;
    private final long warningIntervalMillis;
    private final long awayGraceMillis;
    private final long maxPollGapMillis;

    @Nullable
    private String foregroundPackage;
    private long foregroundSinceMillis = NONE;
    private long awaySinceMillis = NONE;
    private long lastPollMillis = NONE;
    private long countedMillis;
    private long chargedIntervals;
    private long warnedIntervals;

    DoomScrollSessionTracker(@NonNull Set<String> monitoredPackages,
                             long penaltyIntervalMillis,
                             long warningIntervalMillis,
                             long awayGraceMillis,
                             long maxPollGapMillis) {
        this.monitoredPackages = new HashSet<>(monitoredPackages);
        this.penaltyIntervalMillis = penaltyIntervalMillis;
        this.warningIntervalMillis = warningIntervalMillis;
        this.awayGraceMillis = awayGraceMillis;
        this.maxPollGapMillis = maxPollGapMillis;
    }

    /** @return true if a display of this size is in portrait (a square counts as portrait). */
    static boolean isPortrait(int widthPixels, int heightPixels) {
        return heightPixels >= widthPixels;
    }

    void onForeground(@NonNull String packageName, long timestampMillis) {
        if (!monitoredPackages.contains(packageName)) {
            if (foregroundPackage != null) {
                leaveWatchedApp(timestampMillis);
            }
            return;
        }
        if (awaySinceMillis != NONE && timestampMillis - awaySinceMillis >= awayGraceMillis) {
            resetCounters();
        }
        if (foregroundPackage == null) {
            foregroundSinceMillis = timestampMillis;
        }
        foregroundPackage = packageName;
        awaySinceMillis = NONE;
    }

    void onBackground(@NonNull String packageName, long timestampMillis) {
        if (packageName.equals(foregroundPackage)) {
            leaveWatchedApp(timestampMillis);
        }
    }

    /**
     * Counts the time since the previous poll.
     *
     * @param countable whether the screen currently qualifies (portrait and on).
     * @return what became due, or {@code null} if nothing did.
     */
    @Nullable
    Tick onPoll(long nowMillis, boolean countable) {
        long previousPoll = lastPollMillis;
        lastPollMillis = nowMillis;
        if (awaySinceMillis != NONE && nowMillis - awaySinceMillis >= awayGraceMillis) {
            resetCounters();
            awaySinceMillis = NONE;
        }
        String packageName = foregroundPackage;
        if (packageName == null || !countable || previousPoll == NONE
                || nowMillis - previousPoll > maxPollGapMillis) {
            return null;
        }
        long elapsed = nowMillis - Math.max(previousPoll, foregroundSinceMillis);
        if (elapsed <= 0L) {
            return null;
        }
        countedMillis += elapsed;

        long completedPenalties = countedMillis / penaltyIntervalMillis;
        int newPenalties = (int) (completedPenalties - chargedIntervals);
        chargedIntervals = completedPenalties;

        long completedWarnings = countedMillis / warningIntervalMillis;
        long warningMillis = 0L;
        if (completedWarnings > warnedIntervals) {
            warnedIntervals = completedWarnings;
            warningMillis = completedWarnings * warningIntervalMillis;
        }
        return newPenalties > 0 || warningMillis > 0L
                ? new Tick(packageName, newPenalties, warningMillis)
                : null;
    }

    /** Forgets everything, including the poll baseline. */
    void reset() {
        foregroundPackage = null;
        foregroundSinceMillis = NONE;
        awaySinceMillis = NONE;
        lastPollMillis = NONE;
        resetCounters();
    }

    private void leaveWatchedApp(long timestampMillis) {
        foregroundPackage = null;
        foregroundSinceMillis = NONE;
        awaySinceMillis = timestampMillis;
    }

    private void resetCounters() {
        countedMillis = 0L;
        chargedIntervals = 0L;
        warnedIntervals = 0L;
    }
}
