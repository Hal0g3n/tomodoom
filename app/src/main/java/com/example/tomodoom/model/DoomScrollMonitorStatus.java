package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A snapshot of what a doom-scroll monitor saw at its last poll, for the debug panel. Immutable
 * and pure Java. Times are wall-clock milliseconds; {@link #NEVER} means "hasn't happened".
 */
public final class DoomScrollMonitorStatus {

    public static final long NEVER = -1L;

    private final long lastPollMillis;
    private final long pollCount;
    private final long lastGapMillis;
    private final int lastEventCount;
    @Nullable
    private final String foregroundPackage;
    @Nullable
    private final String lastSeenPackage;
    private final boolean countable;
    private final int displayWidth;
    private final int displayHeight;
    private final long sessionCountedMillis;
    private final long sessionChargedMinutes;
    private final int sessionHpLost;
    private final int nextWarningHp;
    private final long hpChargedSinceStart;
    @Nullable
    private final String lastError;
    private final long lastErrorMillis;

    public DoomScrollMonitorStatus(long lastPollMillis, long pollCount, long lastGapMillis,
                                   int lastEventCount,
                                   @Nullable String foregroundPackage,
                                   @Nullable String lastSeenPackage,
                                   boolean countable, int displayWidth, int displayHeight,
                                   long sessionCountedMillis, long sessionChargedMinutes,
                                   int sessionHpLost, int nextWarningHp,
                                   long hpChargedSinceStart,
                                   @Nullable String lastError, long lastErrorMillis) {
        this.lastPollMillis = lastPollMillis;
        this.pollCount = pollCount;
        this.lastGapMillis = lastGapMillis;
        this.lastEventCount = lastEventCount;
        this.foregroundPackage = foregroundPackage;
        this.lastSeenPackage = lastSeenPackage;
        this.countable = countable;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.sessionCountedMillis = sessionCountedMillis;
        this.sessionChargedMinutes = sessionChargedMinutes;
        this.sessionHpLost = sessionHpLost;
        this.nextWarningHp = nextWarningHp;
        this.hpChargedSinceStart = hpChargedSinceStart;
        this.lastError = lastError;
        this.lastErrorMillis = lastErrorMillis;
    }

    /** When the last poll finished, or {@link #NEVER}. */
    public long getLastPollMillis() {
        return lastPollMillis;
    }

    /** Polls since monitoring started. */
    public long getPollCount() {
        return pollCount;
    }

    /** Time between the last two polls, or {@link #NEVER} before the second poll. */
    public long getLastGapMillis() {
        return lastGapMillis;
    }

    /** Foreground/background usage events read at the last poll. */
    public int getLastEventCount() {
        return lastEventCount;
    }

    /** The watched app on screen, or {@code null}. */
    @Nullable
    public String getForegroundPackage() {
        return foregroundPackage;
    }

    /** The last app seen coming to the foreground, watched or not, or {@code null}. */
    @Nullable
    public String getLastSeenPackage() {
        return lastSeenPackage;
    }

    /** Whether the screen qualified at the last poll (on, portrait). */
    public boolean isCountable() {
        return countable;
    }

    /** Display width the monitor read at the last poll (0 if unknown). */
    public int getDisplayWidth() {
        return displayWidth;
    }

    /** Display height the monitor read at the last poll (0 if unknown). */
    public int getDisplayHeight() {
        return displayHeight;
    }

    /** Counted time in the current session. */
    public long getSessionCountedMillis() {
        return sessionCountedMillis;
    }

    /** Whole minutes charged in the current session. */
    public long getSessionChargedMinutes() {
        return sessionChargedMinutes;
    }

    /** HP the current session has cost. */
    public int getSessionHpLost() {
        return sessionHpLost;
    }

    /** HP-lost threshold of the session's next warning. */
    public int getNextWarningHp() {
        return nextWarningHp;
    }

    /** HP reported to the listener since monitoring started. */
    public long getHpChargedSinceStart() {
        return hpChargedSinceStart;
    }

    /** The last unexpected poll error ("Class: message"), or {@code null}. */
    @Nullable
    public String getLastError() {
        return lastError;
    }

    /** When {@link #getLastError()} happened, or {@link #NEVER}. */
    public long getLastErrorMillis() {
        return lastErrorMillis;
    }

    @NonNull
    @Override
    public String toString() {
        return "DoomScrollMonitorStatus{polls=" + pollCount + ", gap=" + lastGapMillis
                + ", events=" + lastEventCount + ", fg=" + foregroundPackage
                + ", lastSeen=" + lastSeenPackage + ", countable=" + countable
                + ", display=" + displayWidth + "x" + displayHeight
                + ", counted=" + sessionCountedMillis + ", charged=" + sessionChargedMinutes
                + ", sessionHp=" + sessionHpLost + ", nextWarning=" + nextWarningHp
                + ", hp=" + hpChargedSinceStart + ", error=" + lastError + "}";
    }
}
