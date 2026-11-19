package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DoomScrollRateTest {

    @Test
    public void clampKeepsValuesInRange() {
        assertEquals(0, DoomScrollRate.clamp(-3));
        assertEquals(0, DoomScrollRate.clamp(0));
        assertEquals(7, DoomScrollRate.clamp(7));
        assertEquals(100, DoomScrollRate.clamp(100));
        assertEquals(100, DoomScrollRate.clamp(250));
    }

    @Test
    public void defaultIsOneHpPerMinute() {
        assertEquals(1, DoomScrollRate.DEFAULT);
    }

    @Test
    public void parseAcceptsWholeNumbers() {
        assertEquals(Integer.valueOf(5), DoomScrollRate.parse("5"));
        assertEquals(Integer.valueOf(12), DoomScrollRate.parse("  12 "));
        assertEquals(Integer.valueOf(0), DoomScrollRate.parse("0"));
        assertEquals(Integer.valueOf(3), DoomScrollRate.parse("+3"));
    }

    @Test
    public void parseClampsOutOfRangeNumbers() {
        assertEquals(Integer.valueOf(0), DoomScrollRate.parse("-4"));
        assertEquals(Integer.valueOf(100), DoomScrollRate.parse("101"));
        assertEquals(Integer.valueOf(100), DoomScrollRate.parse("99999999999999"));
        assertEquals(Integer.valueOf(0), DoomScrollRate.parse("-99999999999999"));
    }

    @Test
    public void parseRejectsNonNumbers() {
        assertNull(DoomScrollRate.parse(null));
        assertNull(DoomScrollRate.parse(""));
        assertNull(DoomScrollRate.parse("   "));
        assertNull(DoomScrollRate.parse("abc"));
        assertNull(DoomScrollRate.parse("1.5"));
        assertNull(DoomScrollRate.parse("5hp"));
    }
}
