package com.example.tomodoom.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DoomScrollLogTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long MIN = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    /** Midnight UTC. */
    private static final long T0 = 20_000 * DAY;

    /** Simulates the monitor: one 1-minute event per counted minute, starting at {@code start}. */
    private static void scroll(DoomScrollLog log, String pkg, long start, int minutes) {
        for (int i = 1; i <= minutes; i++) {
            log.record(pkg, MIN, start + i * MIN + i * 5_000L, UTC); // poll jitter
        }
    }

    @Test
    public void noScrolling_cleanTimeCountsFromTrackingStart() {
        DoomScrollLog log = new DoomScrollLog(T0);
        assertEquals(-1, log.getLastDoomScrollMillis());
        assertEquals(3 * HOUR, log.getCurrentCleanMillis(T0 + 3 * HOUR));
        assertEquals(3 * HOUR, log.getLongestCleanMillis(T0 + 3 * HOUR));
    }

    @Test
    public void consecutiveMinutes_formOneSession_gapStartsAnother() {
        DoomScrollLog log = new DoomScrollLog(T0);
        scroll(log, "a", T0 + HOUR, 10);
        scroll(log, "b", T0 + 3 * HOUR, 3);

        List<DoomScrollLog.Session> sessions = log.sessionsSince(T0);
        assertEquals(2, sessions.size());
        assertTrue(sessions.get(0).getLengthMillis() >= 10 * MIN);
        assertEquals(log.getLongestSessionMillisSince(T0), sessions.get(0).getLengthMillis());
        assertEquals(13 * MIN, log.getMillisOnDay(LocalDays.dayOf(T0, UTC)));
    }

    @Test
    public void appTimes_sumPerPackageAndSortMostFirst() {
        DoomScrollLog log = new DoomScrollLog(T0);
        scroll(log, "a", T0 + HOUR, 2);
        scroll(log, "b", T0 + 2 * HOUR, 5);
        log.record(null, MIN, T0 + 4 * HOUR, UTC);

        List<DoomScrollLog.AppTime> apps = log.getAppTimesSince(LocalDays.dayOf(T0, UTC));
        assertEquals(3, apps.size());
        assertEquals("b", apps.get(0).getPackageName());
        assertEquals(5 * MIN, apps.get(0).getMillis());
        assertEquals("a", apps.get(1).getPackageName());
        assertEquals(DoomScrollLog.UNKNOWN_PACKAGE, apps.get(2).getPackageName());
    }

    @Test
    public void dayBuckets_splitAtLocalMidnight() {
        TimeZone plusTwo = TimeZone.getTimeZone("GMT+02:00");
        DoomScrollLog log = new DoomScrollLog(T0);
        // 21:59 and 22:01 UTC are 23:59 and 00:01 at UTC+2.
        log.record("a", MIN, T0 + 22 * HOUR - MIN, plusTwo);
        log.record("a", MIN, T0 + 22 * HOUR + MIN, plusTwo);
        long day = LocalDays.dayOf(T0 + 22 * HOUR - MIN, plusTwo);
        assertEquals(MIN, log.getMillisOnDay(day));
        assertEquals(MIN, log.getMillisOnDay(day + 1));
    }

    @Test
    public void cleanStreaks_currentAndLongest() {
        DoomScrollLog log = new DoomScrollLog(T0);
        scroll(log, "a", T0 + 5 * HOUR, 1);   // 5 h clean before it
        scroll(log, "a", T0 + 6 * HOUR, 1);   // ~1 h gap
        long last = log.getLastDoomScrollMillis();
        assertEquals(30 * MIN, log.getCurrentCleanMillis(last + 30 * MIN));
        // The first counted minute started 5 s of jitter after T0 + 5 h.
        assertEquals(5 * HOUR + 5_000L, log.getLongestCleanMillis(last + 30 * MIN));
        assertEquals(8 * HOUR, log.getLongestCleanMillis(last + 8 * HOUR));
    }

    @Test
    public void longestClean_survivesPruning() {
        DoomScrollLog log = new DoomScrollLog(T0);
        scroll(log, "a", T0 + 10 * HOUR, 1);
        scroll(log, "a", T0 + 40 * DAY, 1);
        assertEquals(1, log.sessionsSince(0).size());
        long last = log.getLastDoomScrollMillis();
        assertTrue(log.getLongestCleanMillis(last + MIN) > 39 * DAY);
    }

    @Test
    public void json_roundTrips() throws JSONException {
        DoomScrollLog log = new DoomScrollLog(T0);
        scroll(log, "a", T0 + HOUR, 3);
        DoomScrollLog copy = DoomScrollLog.fromJson(log.toJson(), 0);
        assertEquals(T0, copy.getTrackingSinceMillis());
        assertEquals(log.getLastDoomScrollMillis(), copy.getLastDoomScrollMillis());
        assertEquals(3 * MIN, copy.getMillisOnDay(LocalDays.dayOf(T0, UTC)));
        assertFalse(copy.sessionsSince(0).isEmpty());
    }
}
