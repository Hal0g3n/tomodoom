package com.example.tomodoom.doomscroll;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DoomScrollSessionTrackerTest {

    private static final String WATCHED = "com.example.social";
    private static final String OTHER_WATCHED = "com.example.video";
    private static final String UNWATCHED = "com.example.notes";
    private static final long POLL = 15_000L;
    private static final long MINUTE = 60_000L;
    private static final long TEN_MINUTES = 10L * MINUTE;

    private final DoomScrollSessionTracker tracker = new DoomScrollSessionTracker(
            new HashSet<>(Arrays.asList(WATCHED, OTHER_WATCHED)),
            MINUTE, TEN_MINUTES, MINUTE, 45_000L);

    /** Polls every 15 s from {@code from} (exclusive) to {@code to}; returns penalty minutes. */
    private int pollUntil(long from, long to, boolean countable) {
        int minutes = 0;
        for (long now = from + POLL; now <= to; now += POLL) {
            DoomScrollSessionTracker.Tick tick = tracker.onPoll(now, countable);
            if (tick != null) {
                minutes += tick.penaltyIntervals;
            }
        }
        return minutes;
    }

    private void startSession() {
        tracker.onPoll(0L, true);
        tracker.onForeground(WATCHED, 0L);
    }

    @Test
    public void chargesOnePenaltyPerMinute() {
        startSession();

        assertNull(tracker.onPoll(POLL, true));
        assertNull(tracker.onPoll(2L * POLL, true));
        assertNull(tracker.onPoll(3L * POLL, true));
        DoomScrollSessionTracker.Tick tick = tracker.onPoll(MINUTE, true);

        assertEquals(WATCHED, tick.sourcePackage);
        assertEquals(1, tick.penaltyIntervals);
        assertEquals(0L, tick.warningSessionMillis);
        assertEquals(4, pollUntil(MINUTE, 5L * MINUTE, true));
    }

    @Test
    public void carriesPartMinutesAcrossPolls() {
        startSession();
        assertNull(tracker.onPoll(40_000L, true));
        assertNull(tracker.onPoll(50_000L, true));

        assertEquals(1, tracker.onPoll(70_000L, true).penaltyIntervals);
        assertNull(tracker.onPoll(110_000L, true));
        assertEquals(1, tracker.onPoll(120_000L, true).penaltyIntervals);
    }

    @Test
    public void warnsOnceAtEveryTenMinutes() {
        startSession();
        int warnings = 0;
        for (long now = POLL; now <= 35L * MINUTE; now += POLL) {
            DoomScrollSessionTracker.Tick tick = tracker.onPoll(now, true);
            if (tick != null && tick.warningSessionMillis > 0L) {
                warnings++;
                assertEquals(warnings * TEN_MINUTES, tick.warningSessionMillis);
                assertEquals(warnings * TEN_MINUTES, now);
            }
        }
        assertEquals(3, warnings);
    }

    @Test
    public void landscapePausesWithoutLosingProgress() {
        startSession();
        assertNull(tracker.onPoll(30_000L, true));

        assertEquals(0, pollUntil(30_000L, 10L * MINUTE, false));

        assertNull(tracker.onPoll(10L * MINUTE + 15_000L, true));
        assertEquals(1, tracker.onPoll(10L * MINUTE + 30_000L, true).penaltyIntervals);
    }

    @Test
    public void awayForTheGracePeriodEndsTheSession() {
        startSession();
        pollUntil(0L, 9L * MINUTE, true);
        tracker.onBackground(WATCHED, 9L * MINUTE);
        pollUntil(9L * MINUTE, 10L * MINUTE, true);

        tracker.onForeground(WATCHED, 10L * MINUTE);
        int warnings = 0;
        for (long now = 10L * MINUTE + POLL; now <= 19L * MINUTE; now += POLL) {
            DoomScrollSessionTracker.Tick tick = tracker.onPoll(now, true);
            if (tick != null && tick.warningSessionMillis > 0L) {
                warnings++;
            }
        }
        assertEquals(0, warnings);
    }

    @Test
    public void awayGapSpanningOnePollBatchAlsoEndsTheSession() {
        startSession();
        pollUntil(0L, 9L * MINUTE, true);

        tracker.onBackground(WATCHED, 9L * MINUTE);
        tracker.onForeground(WATCHED, 10L * MINUTE);

        tracker.onPoll(10L * MINUTE + POLL, true);
        assertEquals(0, pollUntil(10L * MINUTE + POLL, 10L * MINUTE + 3L * POLL, true));
    }

    @Test
    public void inAppScreenChangesKeepTheSession() {
        startSession();
        assertNull(tracker.onPoll(45_000L, true));

        tracker.onBackground(WATCHED, 45_100L);
        tracker.onForeground(WATCHED, 45_200L);

        assertEquals(1, pollUntil(45_000L, 75_000L, true));
    }

    @Test
    public void hoppingBetweenWatchedAppsKeepsTheSession() {
        startSession();
        assertNull(tracker.onPoll(45_000L, true));

        tracker.onBackground(WATCHED, 45_100L);
        tracker.onForeground(OTHER_WATCHED, 45_200L);

        DoomScrollSessionTracker.Tick tick = tracker.onPoll(60_000L, true);
        assertNull(tick);
        tick = tracker.onPoll(75_000L, true);
        assertEquals(OTHER_WATCHED, tick.sourcePackage);
        assertEquals(1, tick.penaltyIntervals);
    }

    @Test
    public void ignoresUnwatchedApps() {
        tracker.onPoll(0L, true);
        tracker.onForeground(UNWATCHED, 0L);

        assertEquals(0, pollUntil(0L, 20L * MINUTE, true));
    }

    @Test
    public void switchingToAnUnwatchedAppStopsCharging() {
        startSession();
        tracker.onForeground(UNWATCHED, 0L);

        assertEquals(0, pollUntil(0L, 5L * MINUTE, true));
    }

    @Test
    public void neverChargesReplayedHistory() {
        tracker.onForeground(WATCHED, 0L);
        tracker.onBackground(WATCHED, 20L * MINUTE);

        assertNull(tracker.onPoll(20L * MINUTE + 1L, true));
        assertEquals(0, pollUntil(20L * MINUTE + 1L, 30L * MINUTE, true));
    }

    @Test
    public void firstPollOnlySetsTheBaselineForAReplayedOpenSession() {
        tracker.onForeground(WATCHED, 0L);

        assertNull(tracker.onPoll(20L * MINUTE, true));
        assertNull(tracker.onPoll(20L * MINUTE + POLL, true));
        assertEquals(1, pollUntil(20L * MINUTE + POLL, 21L * MINUTE, true));
    }

    @Test
    public void skipsLongGapsBetweenPolls() {
        startSession();
        assertNull(tracker.onPoll(POLL, true));

        assertNull(tracker.onPoll(10L * MINUTE, true));
        assertNull(tracker.onPoll(10L * MINUTE + POLL, true));
    }

    @Test
    public void countsOnlyFromTheMomentTheAppOpened() {
        tracker.onPoll(0L, true);
        tracker.onForeground(WATCHED, 14_000L);

        assertNull(tracker.onPoll(POLL, true));
        assertNull(tracker.onPoll(60_000L - 15_000L, true));
        assertNull(tracker.onPoll(60_000L, true));
        assertEquals(1, tracker.onPoll(75_000L, true).penaltyIntervals);
    }

    @Test
    public void unwatchingTheOpenAppStopsCharging() {
        startSession();
        assertEquals(2, pollUntil(0L, 2L * MINUTE, true));

        tracker.setMonitoredPackages(Collections.singleton(OTHER_WATCHED), 2L * MINUTE);

        assertEquals(0, pollUntil(2L * MINUTE, 10L * MINUTE, true));
    }

    @Test
    public void watchingTheOpenAppStartsChargingFromThatMoment() {
        tracker.onPoll(0L, true);
        tracker.onForeground(UNWATCHED, 0L);
        assertEquals(0, pollUntil(0L, 5L * MINUTE, true));

        tracker.setMonitoredPackages(
                new HashSet<>(Arrays.asList(WATCHED, UNWATCHED)), 5L * MINUTE);

        DoomScrollSessionTracker.Tick tick = null;
        for (long now = 5L * MINUTE + POLL; now <= 6L * MINUTE; now += POLL) {
            tick = tracker.onPoll(now, true);
        }
        assertEquals(UNWATCHED, tick.sourcePackage);
        assertEquals(1, tick.penaltyIntervals);
    }

    @Test
    public void watchingAnAppThatAlreadyLeftChargesNothing() {
        tracker.onPoll(0L, true);
        tracker.onForeground(UNWATCHED, 0L);
        tracker.onBackground(UNWATCHED, POLL);

        tracker.setMonitoredPackages(Collections.singleton(UNWATCHED), 2L * POLL);

        assertEquals(0, pollUntil(0L, 5L * MINUTE, true));
    }

    @Test
    public void unchangedWatchedSetKeepsTheSession() {
        startSession();
        assertNull(tracker.onPoll(30_000L, true));

        tracker.setMonitoredPackages(new HashSet<>(Arrays.asList(WATCHED, OTHER_WATCHED)), 30_000L);

        assertEquals(1, pollUntil(30_000L, MINUTE, true));
    }

    @Test
    public void portraitMeansAtLeastAsTallAsWide() {
        assertTrue(DoomScrollSessionTracker.isPortrait(1080, 2400));
        assertTrue(DoomScrollSessionTracker.isPortrait(1000, 1000));
        assertFalse(DoomScrollSessionTracker.isPortrait(2400, 1080));
    }

    @Test
    public void exposesSessionStateForTheDebugStatus() {
        assertNull(tracker.getForegroundPackage());
        assertNull(tracker.getLastSeenPackage());

        tracker.onForeground(UNWATCHED, 0L);
        assertNull(tracker.getForegroundPackage());
        assertEquals(UNWATCHED, tracker.getLastSeenPackage());

        startSession();
        pollUntil(0L, 90_000L, true);
        assertEquals(WATCHED, tracker.getForegroundPackage());
        assertEquals(WATCHED, tracker.getLastSeenPackage());
        assertEquals(90_000L, tracker.getCountedMillis());
        assertEquals(1L, tracker.getChargedIntervals());

        tracker.reset();
        assertNull(tracker.getForegroundPackage());
        assertEquals(0L, tracker.getCountedMillis());
        assertEquals(0L, tracker.getChargedIntervals());
    }
}
