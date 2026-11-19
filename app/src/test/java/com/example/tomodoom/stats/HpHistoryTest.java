package com.example.tomodoom.stats;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class HpHistoryTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long MIN = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long T0 = TimeUnit.DAYS.toMillis(20_000);

    @Test
    public void cause_isParsedFromReason() {
        assertEquals(HpHistory.Cause.CHAT, HpHistory.Cause.fromReason("quiz_passed:q1"));
        assertEquals(HpHistory.Cause.CHAT, HpHistory.Cause.fromReason("mcp:award_hp"));
        assertEquals(HpHistory.Cause.DOOM_SCROLL,
                HpHistory.Cause.fromReason("doom_scroll:com.instagram.android"));
        assertEquals(HpHistory.Cause.OTHER, HpHistory.Cause.fromReason("debug"));
        assertEquals(HpHistory.Cause.OTHER, HpHistory.Cause.fromReason(null));
    }

    @Test
    public void recordChange_totalsByCause() {
        HpHistory h = new HpHistory();
        h.recordChange(T0, 50, 60, 100, "quiz_passed:q1", UTC);
        h.recordChange(T0 + HOUR, 60, 58, 100, "doom_scroll:x", UTC);
        h.recordChange(T0 + 2 * HOUR, 58, 57, 100, "debug", UTC);
        h.recordChange(T0 + 3 * HOUR, 57, 57, 100, "doom_scroll:x", UTC); // clamped: no change

        HpHistory.Totals totals = h.totalsSince(LocalDays.dayOf(T0, UTC));
        assertEquals(10, totals.getGainedFromChat());
        assertEquals(0, totals.getGainedOther());
        assertEquals(2, totals.getLostToDoomScroll());
        assertEquals(1, totals.getLostOther());
        assertEquals(0, h.totalsSince(LocalDays.dayOf(T0, UTC) + 1).getLostToDoomScroll());
    }

    @Test
    public void recordSample_skipsDuplicatesAndCoalescesBursts() {
        HpHistory h = new HpHistory();
        h.recordSample(T0, 50, 100);
        h.recordSample(T0 + HOUR, 50, 100); // unchanged
        assertEquals(1, h.sampleCount());

        h.recordSample(T0 + 2 * HOUR, 49, 100);
        h.recordSample(T0 + 2 * HOUR + MIN, 48, 100);
        h.recordSample(T0 + 2 * HOUR + 2 * MIN, 47, 100);
        assertEquals(2, h.sampleCount());
        List<HpHistory.Sample> samples = h.samplesSince(T0 - 1);
        assertEquals(47, samples.get(1).getHp());
        assertEquals(T0 + 2 * HOUR, samples.get(1).getTimestampMillis());

        h.recordSample(T0 + 2 * HOUR + 10 * MIN, 46, 100);
        assertEquals(3, h.sampleCount());
    }

    @Test
    public void samplesSince_startsWithValueAtWindowStart() {
        HpHistory h = new HpHistory();
        h.recordSample(T0, 50, 100);
        h.recordSample(T0 + 10 * HOUR, 40, 100);
        List<HpHistory.Sample> samples = h.samplesSince(T0 + 5 * HOUR);
        assertEquals(2, samples.size());
        assertEquals(T0 + 5 * HOUR, samples.get(0).getTimestampMillis());
        assertEquals(50, samples.get(0).getHp());
        assertEquals(40, samples.get(1).getHp());
    }

    @Test
    public void oldSamplesArePruned_keepingOneAnchor() {
        HpHistory h = new HpHistory();
        h.recordSample(T0, 50, 100);
        h.recordSample(T0 + HOUR, 49, 100);
        h.recordSample(T0 + TimeUnit.DAYS.toMillis(10), 48, 100);
        assertEquals(2, h.sampleCount());
        assertEquals(49, h.samplesSince(T0 + TimeUnit.DAYS.toMillis(9)).get(0).getHp());
    }

    @Test
    public void json_roundTrips() throws JSONException {
        HpHistory h = new HpHistory();
        h.recordChange(T0, 50, 60, 100, "quiz_passed:q1", UTC);
        h.recordChange(T0 + HOUR, 60, 55, 100, "doom_scroll:x", UTC);
        HpHistory copy = HpHistory.fromJson(h.toJson());
        assertEquals(h.sampleCount(), copy.sampleCount());
        HpHistory.Totals totals = copy.totalsSince(0);
        assertEquals(10, totals.getGainedFromChat());
        assertEquals(5, totals.getLostToDoomScroll());
    }
}
