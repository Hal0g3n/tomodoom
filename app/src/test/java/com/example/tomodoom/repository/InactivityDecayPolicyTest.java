package com.example.tomodoom.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InactivityDecayPolicyTest {

    private static final long MINUTE = 60L * 1000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long GRACE = InactivityDecayPolicy.GRACE_MILLIS;

    @Test
    public void noLossWithinGrace() {
        assertEquals(0, InactivityDecayPolicy.hpLoss(100, 0));
        assertEquals(0, InactivityDecayPolicy.hpLoss(100, 29 * MINUTE));
        assertEquals(0, InactivityDecayPolicy.hpLoss(100, GRACE));
    }

    @Test
    public void thirtyPercentPerEightHoursAfterGrace() {
        assertEquals(30, InactivityDecayPolicy.hpLoss(100, GRACE + 8 * HOUR));
        assertEquals(15, InactivityDecayPolicy.hpLoss(50, GRACE + 8 * HOUR));
    }

    @Test
    public void flattensOutOverDays() {
        int day = InactivityDecayPolicy.hpLoss(100, GRACE + 24 * HOUR);
        int twoDays = InactivityDecayPolicy.hpLoss(100, 48 * HOUR);
        int week = InactivityDecayPolicy.hpLoss(100, 7 * 24 * HOUR);
        assertEquals(66, day);
        assertTrue("two days leaves about 12 HP, was " + (100 - twoDays),
                100 - twoDays >= 10 && 100 - twoDays <= 14);
        assertEquals(100, week);
    }

    @Test
    public void memorylessAcrossVisits() {
        // Two 8 h absences cost about the same as one 16 h absence (ignoring the grace).
        int first = InactivityDecayPolicy.hpLoss(100, GRACE + 8 * HOUR);
        int second = InactivityDecayPolicy.hpLoss(100 - first, GRACE + 8 * HOUR);
        int once = InactivityDecayPolicy.hpLoss(100, GRACE + 16 * HOUR);
        assertTrue(Math.abs((first + second) - once) <= 1);
    }

    @Test
    public void reachesZeroAndNeverExceedsCurrentHp() {
        assertEquals(1, InactivityDecayPolicy.hpLoss(1, GRACE + 24 * HOUR));
        assertEquals(0, InactivityDecayPolicy.hpLoss(0, 30 * 24 * HOUR));
        assertEquals(100, InactivityDecayPolicy.hpLoss(100, Long.MAX_VALUE));
    }

    @Test
    public void clockGoingBackwardsLosesNothing() {
        assertEquals(0, InactivityDecayPolicy.hpLoss(100, -5 * HOUR));
    }
}
