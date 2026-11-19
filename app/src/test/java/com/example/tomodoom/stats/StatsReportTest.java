package com.example.tomodoom.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class StatsReportTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long MIN = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long T0 = 20_000 * DAY;

    @Test
    public void empty_hasNoDoomScrollingAndNoTokens() {
        StatsSnapshot snapshot = new StatsSnapshot(new TokenUsageLedger(), new HpHistory(),
                new DoomScrollLog(T0));
        StatsReport report = StatsReport.of(snapshot, T0 + HOUR, UTC);
        assertFalse(report.hasDoomScrolled());
        assertEquals(HOUR, report.getCurrentCleanMillis());
        assertEquals(0, report.getWeekTotalMillis());
        assertTrue(report.getTokenUsage().isEmpty());
        assertTrue(report.getHpWeek().isEmpty());
    }

    @Test
    public void week_coversTodayAndSixDaysBefore() {
        DoomScrollLog doom = new DoomScrollLog(T0);
        doom.record("a", 2 * MIN, T0 + HOUR, UTC);               // 8 days before "now": outside
        doom.record("a", 3 * MIN, T0 + 2 * DAY + HOUR, UTC);     // day index 0
        doom.record("b", 4 * MIN, T0 + 8 * DAY + HOUR, UTC);     // today
        long now = T0 + 8 * DAY + 2 * HOUR;
        StatsReport report = StatsReport.of(new StatsSnapshot(new TokenUsageLedger(),
                new HpHistory(), doom), now, UTC);

        long[] days = report.getWeekDayMillis();
        assertEquals(StatsReport.WEEK_DAYS, days.length);
        assertEquals(3 * MIN, days[0]);
        assertEquals(4 * MIN, days[6]);
        assertEquals(7 * MIN, report.getWeekTotalMillis());
        assertEquals(4 * MIN, report.getTodayMillis());
        assertEquals(1, report.getSessionsToday());
        assertEquals(T0 + 2 * DAY, report.getWeekDayStarts()[0]);
        assertEquals(2, report.getWeekApps().size());
        assertTrue(report.hasDoomScrolled());
        assertEquals(HOUR, report.getCurrentCleanMillis());
    }
}
