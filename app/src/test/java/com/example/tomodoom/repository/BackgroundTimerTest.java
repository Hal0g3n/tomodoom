package com.example.tomodoom.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundTimerTest {

    private static final long TEN_MINUTES = 10 * 60 * 1000L;

    @Test
    public void firstForeground_withoutBackground_isNotExpired() {
        assertFalse(new BackgroundTimer().onForeground(5_000L, TEN_MINUTES));
    }

    @Test
    public void shortAbsence_isNotExpired() {
        BackgroundTimer timer = new BackgroundTimer();
        timer.onBackground(1_000L);
        assertFalse(timer.onForeground(1_000L + TEN_MINUTES - 1, TEN_MINUTES));
    }

    @Test
    public void rotation_isNotExpired() {
        BackgroundTimer timer = new BackgroundTimer();
        timer.onBackground(1_000L);
        assertFalse(timer.onForeground(1_005L, TEN_MINUTES));
    }

    @Test
    public void tenMinutesAway_isExpired() {
        BackgroundTimer timer = new BackgroundTimer();
        timer.onBackground(1_000L);
        assertTrue(timer.onForeground(1_000L + TEN_MINUTES, TEN_MINUTES));
    }

    @Test
    public void expiryIsReportedOnce() {
        BackgroundTimer timer = new BackgroundTimer();
        timer.onBackground(0L);
        assertTrue(timer.onForeground(TEN_MINUTES * 2, TEN_MINUTES));
        assertFalse(timer.onForeground(TEN_MINUTES * 3, TEN_MINUTES));
    }

    @Test
    public void latestBackgroundCounts() {
        BackgroundTimer timer = new BackgroundTimer();
        timer.onBackground(0L);
        assertFalse(timer.onForeground(1_000L, TEN_MINUTES));
        timer.onBackground(TEN_MINUTES);
        assertFalse(timer.onForeground(TEN_MINUTES + 1_000L, TEN_MINUTES));
    }
}
