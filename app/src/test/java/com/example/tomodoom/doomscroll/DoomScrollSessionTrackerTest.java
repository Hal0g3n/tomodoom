package com.example.tomodoom.doomscroll;

import com.example.tomodoom.model.DoomScrollEvent;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DoomScrollSessionTrackerTest {

    private static final String TRACKED_PACKAGE = "com.example.social";
    private static final long MIN_SESSION_MILLIS = 15L * 60L * 1000L;
    private static final long COOLDOWN_MILLIS = 30L * 60L * 1000L;

    @Test
    public void emitsOnePenaltyAtTheSessionThreshold() {
        DoomScrollSessionTracker tracker = newTracker();
        long startedAt = 1_000L;

        assertNull(tracker.onForeground(TRACKED_PACKAGE, startedAt));
        assertNull(tracker.onPoll(startedAt + MIN_SESSION_MILLIS - 1L));
        DoomScrollEvent event = tracker.onPoll(startedAt + MIN_SESSION_MILLIS);

        assertEquals(TRACKED_PACKAGE, event.getSourcePackage());
        assertEquals(MIN_SESSION_MILLIS, event.getDurationMillis());
        assertEquals(DoomScrollEvent.DEFAULT_HP_PENALTY, event.getHpPenalty());
        assertNull(tracker.onPoll(startedAt + MIN_SESSION_MILLIS + 10_000L));
    }

    @Test
    public void suppressesRapidRepeatSessionsButAllowsOneAfterCooldown() {
        DoomScrollSessionTracker tracker = newTracker();
        long firstStart = 0L;

        tracker.onForeground(TRACKED_PACKAGE, firstStart);
        assertEquals(
                firstStart + MIN_SESSION_MILLIS,
                tracker.onPoll(firstStart + MIN_SESSION_MILLIS).getTimestampMillis());
        tracker.onBackground(TRACKED_PACKAGE, firstStart + MIN_SESSION_MILLIS + 1L);

        long repeatStart = firstStart + MIN_SESSION_MILLIS + 2L;
        tracker.onForeground(TRACKED_PACKAGE, repeatStart);
        assertNull(tracker.onPoll(repeatStart + MIN_SESSION_MILLIS));
        tracker.onBackground(TRACKED_PACKAGE, repeatStart + MIN_SESSION_MILLIS + 1L);

        long laterStart = firstStart + MIN_SESSION_MILLIS + COOLDOWN_MILLIS + 1L;
        tracker.onForeground(TRACKED_PACKAGE, laterStart);
        assertEquals(
                laterStart + MIN_SESSION_MILLIS,
                tracker.onPoll(laterStart + MIN_SESSION_MILLIS).getTimestampMillis());
    }

    @Test
    public void ignoresUnmonitoredPackages() {
        DoomScrollSessionTracker tracker = newTracker();
        tracker.onForeground("com.example.other", 0L);

        assertNull(tracker.onPoll(MIN_SESSION_MILLIS * 2L));
    }

    private static DoomScrollSessionTracker newTracker() {
        return new DoomScrollSessionTracker(
                Collections.singleton(TRACKED_PACKAGE),
                MIN_SESSION_MILLIS,
                COOLDOWN_MILLIS);
    }
}
