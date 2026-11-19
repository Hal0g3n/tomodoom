package com.example.tomodoom.stats;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class DurationSplitTest {

    private static final long MIN = TimeUnit.MINUTES.toMillis(1);

    @Test
    public void picksTheTwoLargestUnits() {
        assertEquals(DurationSplit.Unit.UNDER_MINUTE, DurationSplit.of(59_999).getUnit());
        assertEquals(DurationSplit.Unit.UNDER_MINUTE, DurationSplit.of(-5).getUnit());

        DurationSplit minutes = DurationSplit.of(12 * MIN + 30_000);
        assertEquals(DurationSplit.Unit.MINUTES, minutes.getUnit());
        assertEquals(12, minutes.getMajor());

        DurationSplit hours = DurationSplit.of(135 * MIN);
        assertEquals(DurationSplit.Unit.HOURS, hours.getUnit());
        assertEquals(2, hours.getMajor());
        assertEquals(15, hours.getMinor());

        DurationSplit days = DurationSplit.of((3 * 24 + 4) * 60 * MIN + 59 * MIN);
        assertEquals(DurationSplit.Unit.DAYS, days.getUnit());
        assertEquals(3, days.getMajor());
        assertEquals(4, days.getMinor());
    }
}
