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
 * for the grace period ends it, so in-app screen changes and app-hopping don't reset the timer.
 * The watched set can change at any time ({@link #setMonitoredPackages}).</p>
 *
 * <p>Warnings follow the HP a session has cost ({@link DoomScrollWarningPolicy}: 10, 20, 40 …),
 * so they track the configured HP per interval, and a new session starts again at 10.</p>
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
        /** HP those intervals cost; 0 if none. */
        final int hpPenalty;
        /** The HP-lost threshold this warning marks; 0 if no warning is due. */
        final int warningHpLost;
        /** Counted session length at this poll. */
        final long sessionCountedMillis;

        Tick(@NonNull String sourcePackage, int penaltyIntervals, int hpPenalty,
             int warningHpLost, long sessionCountedMillis) {
            this.sourcePackage = sourcePackage;
            this.penaltyIntervals = penaltyIntervals;
            this.hpPenalty = hpPenalty;
            this.warningHpLost = warningHpLost;
            this.sessionCountedMillis = sessionCountedMillis;
        }
    }

    @NonNull
    private Set<String> monitoredPackages;
    private final long penaltyIntervalMillis;
    private final long awayGraceMillis;
    private final long maxPollGapMillis;

    /** The watched app on screen, or {@code null}. */
    @Nullable
    private String foregroundPackage;
    /** The last app seen coming to the foreground, watched or not, until it leaves. */
    @Nullable
    private String lastSeenPackage;
    private long foregroundSinceMillis = NONE;
    private long awaySinceMillis = NONE;
    private long lastPollMillis = NONE;
    private long countedMillis;
    private long chargedIntervals;
    private int sessionHpLost;
    private int warnedHp;

    DoomScrollSessionTracker(@NonNull Set<String> monitoredPackages,
                             long penaltyIntervalMillis,
                             long awayGraceMillis,
                             long maxPollGapMillis) {
        this.monitoredPackages = new HashSet<>(monitoredPackages);
        this.penaltyIntervalMillis = penaltyIntervalMillis;
        this.awayGraceMillis = awayGraceMillis;
        this.maxPollGapMillis = maxPollGapMillis;
    }

    /** @return true if a display of this size is in portrait (a square counts as portrait). */
    static boolean isPortrait(int widthPixels, int heightPixels) {
        return heightPixels >= widthPixels;
    }

    /**
     * Replaces the watched set. A session in an app that is no longer watched stops counting at
     * {@code nowMillis}; an app on screen that has just become watched starts counting then.
     */
    void setMonitoredPackages(@NonNull Set<String> packages, long nowMillis) {
        if (packages.equals(monitoredPackages)) {
            return;
        }
        monitoredPackages = new HashSet<>(packages);
        if (foregroundPackage != null && !monitoredPackages.contains(foregroundPackage)) {
            leaveWatchedApp(nowMillis);
        } else if (foregroundPackage == null && lastSeenPackage != null
                && monitoredPackages.contains(lastSeenPackage)) {
            onForeground(lastSeenPackage, nowMillis);
        }
    }

    void onForeground(@NonNull String packageName, long timestampMillis) {
        lastSeenPackage = packageName;
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
        if (packageName.equals(lastSeenPackage)) {
            lastSeenPackage = null;
        }
        if (packageName.equals(foregroundPackage)) {
            leaveWatchedApp(timestampMillis);
        }
    }

    /** {@link #onPoll(long, boolean, int)} at 1 HP per interval. */
    @Nullable
    Tick onPoll(long nowMillis, boolean countable) {
        return onPoll(nowMillis, countable, 1);
    }

    /**
     * Counts the time since the previous poll.
     *
     * @param countable     whether the screen currently qualifies (portrait and on).
     * @param hpPerInterval HP each newly completed interval costs (negative counts as 0).
     * @return what became due, or {@code null} if nothing did.
     */
    @Nullable
    Tick onPoll(long nowMillis, boolean countable, int hpPerInterval) {
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

        long hp = (long) newPenalties * Math.max(0, hpPerInterval);
        int hpPenalty = (int) Math.min(Integer.MAX_VALUE, hp);
        sessionHpLost = (int) Math.min(Integer.MAX_VALUE, (long) sessionHpLost + hpPenalty);

        int warning = DoomScrollWarningPolicy.warningDue(sessionHpLost, warnedHp);
        if (warning > 0) {
            warnedHp = warning;
        }
        return newPenalties > 0 || warning > 0
                ? new Tick(packageName, newPenalties, hpPenalty, warning, countedMillis)
                : null;
    }

    /** The watched app on screen, or {@code null}. */
    @Nullable
    String getForegroundPackage() {
        return foregroundPackage;
    }

    /** The last app seen coming to the foreground (watched or not) until it leaves, or {@code null}. */
    @Nullable
    String getLastSeenPackage() {
        return lastSeenPackage;
    }

    /** Counted time in the current session. */
    long getCountedMillis() {
        return countedMillis;
    }

    /** Whole penalty intervals charged in the current session. */
    long getChargedIntervals() {
        return chargedIntervals;
    }

    /** HP the current session has cost. */
    int getSessionHpLost() {
        return sessionHpLost;
    }

    /** The HP-lost threshold of the session's next warning. */
    int getNextWarningHp() {
        return DoomScrollWarningPolicy.nextThreshold(warnedHp);
    }

    /** Forgets everything, including the poll baseline. */
    void reset() {
        foregroundPackage = null;
        lastSeenPackage = null;
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
        sessionHpLost = 0;
        warnedHp = 0;
    }
}
