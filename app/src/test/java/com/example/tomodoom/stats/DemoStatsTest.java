package com.example.tomodoom.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class DemoStatsTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    /** 18:00 on some day, so most of today has events. */
    private static final long NOW = 20_000 * TimeUnit.DAYS.toMillis(1) + 18 * HOUR;

    @Test
    public void fillsEveryPartOfThePage() {
        for (long seed = 0; seed < 50; seed++) {
            StatsReport report = StatsReport.of(DemoStats.generate(NOW, UTC, 63, 100, seed),
                    NOW, UTC);
            assertTrue(report.hasDoomScrolled());
            assertTrue(report.getWeekTotalMillis() > 0);
            assertFalse(report.getWeekApps().isEmpty());
            assertFalse(report.getTokenUsage().isEmpty());
            assertTrue(report.getHpWeekTotals().getGainedFromChat() > 0);
            assertTrue(report.getHpWeekTotals().getLostToDoomScroll() > 0);
            assertTrue(report.getCurrentCleanMillis() >= DemoStats.QUIET_BEFORE_NOW_MILLIS);
            assertTrue(report.getTrackingSinceMillis() > NOW - StatsReport.WEEK_WINDOW_MILLIS);
        }
    }

    @Test
    public void hpLineEndsAtTheRealHpAndStaysInRange() {
        for (long seed = 0; seed < 50; seed++) {
            List<HpHistory.Sample> week = StatsReport.of(
                    DemoStats.generate(NOW, UTC, 37, 100, seed), NOW, UTC).getHpWeek();
            assertEquals(37, week.get(week.size() - 1).getHp());
            for (HpHistory.Sample sample : week) {
                assertTrue(sample.getHp() >= 0 && sample.getHp() <= 100);
                assertTrue(sample.getTimestampMillis() <= NOW);
            }
        }
    }

    @Test
    public void sameSeed_sameData() {
        StatsReport a = StatsReport.of(DemoStats.generate(NOW, UTC, 50, 100, 7), NOW, UTC);
        StatsReport b = StatsReport.of(DemoStats.generate(NOW, UTC, 50, 100, 7), NOW, UTC);
        assertEquals(a.getWeekTotalMillis(), b.getWeekTotalMillis());
        assertEquals(a.getTokenTotal().getTotalTokens(), b.getTokenTotal().getTotalTokens());
    }

    @Test
    public void earlyMorning_noEventsInTheFuture() {
        long earlyNow = NOW - 17 * HOUR; // 01:00
        StatsReport report = StatsReport.of(DemoStats.generate(earlyNow, UTC, 50, 100, 3),
                earlyNow, UTC);
        assertTrue(report.getLastDoomScrollMillis() <= earlyNow);
        for (HpHistory.Sample sample : report.getHpWeek()) {
            assertTrue(sample.getTimestampMillis() <= earlyNow);
        }
    }
}
