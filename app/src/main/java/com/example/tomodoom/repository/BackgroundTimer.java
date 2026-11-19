package com.example.tomodoom.repository;

/**
 * Measures how long the app spent in the background, to decide whether the chat should start
 * over. Times come from a monotonic clock supplied by the caller (the app passes
 * {@code SystemClock.elapsedRealtime()}, which keeps counting in deep sleep). Pure Java and
 * thread-safe.
 */
public final class BackgroundTimer {

    private static final long IN_FOREGROUND = -1L;

    private long backgroundSinceMs = IN_FOREGROUND;

    /** The app has just gone to the background. */
    public synchronized void onBackground(long nowMs) {
        backgroundSinceMs = nowMs;
    }

    /**
     * The app has come back to the foreground.
     *
     * @return true if it was in the background for at least {@code thresholdMs}. Returns false
     * when no {@link #onBackground} came first (e.g. the first launch).
     */
    public synchronized boolean onForeground(long nowMs, long thresholdMs) {
        if (backgroundSinceMs == IN_FOREGROUND) {
            return false;
        }
        long awayMs = nowMs - backgroundSinceMs;
        backgroundSinceMs = IN_FOREGROUND;
        return awayMs >= thresholdMs;
    }
}
