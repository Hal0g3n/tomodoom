package com.example.tomodoom.doomscroll;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DoomScrollWarningPolicyTest {

    @Test
    public void thresholdsDoubleFromTen() {
        assertEquals(10, DoomScrollWarningPolicy.nextThreshold(0));
        assertEquals(20, DoomScrollWarningPolicy.nextThreshold(10));
        assertEquals(40, DoomScrollWarningPolicy.nextThreshold(20));
        assertEquals(80, DoomScrollWarningPolicy.nextThreshold(40));
    }

    @Test
    public void noWarningBelowTenHp() {
        assertEquals(0, DoomScrollWarningPolicy.warningDue(0, 0));
        assertEquals(0, DoomScrollWarningPolicy.warningDue(9, 0));
        assertEquals(10, DoomScrollWarningPolicy.warningDue(10, 0));
    }

    @Test
    public void warnsOncePerThreshold() {
        assertEquals(0, DoomScrollWarningPolicy.warningDue(19, 10));
        assertEquals(20, DoomScrollWarningPolicy.warningDue(20, 10));
        assertEquals(0, DoomScrollWarningPolicy.warningDue(39, 20));
        assertEquals(40, DoomScrollWarningPolicy.warningDue(40, 20));
    }

    @Test
    public void aBigJumpGivesOneWarningForTheHighestThreshold() {
        assertEquals(40, DoomScrollWarningPolicy.warningDue(45, 0));
        assertEquals(80, DoomScrollWarningPolicy.warningDue(100, 10));
    }

    @Test
    public void neverOverflows() {
        int last = 10;
        for (int i = 0; i < 40; i++) {
            int next = DoomScrollWarningPolicy.nextThreshold(last);
            assertEquals(true, next >= last);
            last = next;
        }
        assertEquals(Integer.MAX_VALUE, last);
        assertEquals(0, DoomScrollWarningPolicy.warningDue(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE,
                DoomScrollWarningPolicy.warningDue(Integer.MAX_VALUE, 0));
    }
}
