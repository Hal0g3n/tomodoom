package com.example.tomodoom.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * The pet's HP over time, for the Stats chart, plus the HP gained and lost per day by cause.
 *
 * <p>Samples form a step line: from a sample's time, HP is that sample's value until the next
 * one. Changes less than {@link #SAMPLE_SPACING_MILLIS} after a sample update it in place, so a
 * long doom-scroll (1 HP per minute) costs one sample per few minutes. Samples are kept for
 * {@link #SAMPLE_RETENTION_MILLIS}, daily totals for {@link #DAY_RETENTION} days.</p>
 *
 * <p>Pure Java; not thread-safe ({@code StatsRepository} guards it with its lock).</p>
 */
public final class HpHistory {

    static final long SAMPLE_SPACING_MILLIS = TimeUnit.MINUTES.toMillis(5);
    static final long SAMPLE_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(8);
    static final int MAX_SAMPLES = 2_500;
    static final int DAY_RETENTION = 30;

    /** Where an HP change came from, parsed from the repository's reason tag. */
    public enum Cause {
        /** A quiz passed in chat, or an HP award through the MCP tool. */
        CHAT,
        /** A doom-scroll penalty. */
        DOOM_SCROLL,
        /** Anything else (e.g. debug). */
        OTHER;

        @NonNull
        public static Cause fromReason(@Nullable String reason) {
            if (reason == null) {
                return OTHER;
            }
            if (reason.startsWith("quiz_passed:") || reason.startsWith("mcp:")) {
                return CHAT;
            }
            if (reason.startsWith("doom_scroll:")) {
                return DOOM_SCROLL;
            }
            return OTHER;
        }
    }

    /** One point of the HP line. Immutable. */
    public static final class Sample {
        private final long timestampMillis;
        private final int hp;
        private final int maxHp;

        public Sample(long timestampMillis, int hp, int maxHp) {
            this.timestampMillis = timestampMillis;
            this.hp = hp;
            this.maxHp = maxHp;
        }

        public long getTimestampMillis() {
            return timestampMillis;
        }

        public int getHp() {
            return hp;
        }

        public int getMaxHp() {
            return maxHp;
        }
    }

    /** HP gained and lost over a period. Immutable. */
    public static final class Totals {
        private final int gainedFromChat;
        private final int gainedOther;
        private final int lostToDoomScroll;
        private final int lostOther;

        Totals(int gainedFromChat, int gainedOther, int lostToDoomScroll, int lostOther) {
            this.gainedFromChat = gainedFromChat;
            this.gainedOther = gainedOther;
            this.lostToDoomScroll = lostToDoomScroll;
            this.lostOther = lostOther;
        }

        public int getGainedFromChat() {
            return gainedFromChat;
        }

        public int getGainedOther() {
            return gainedOther;
        }

        public int getLostToDoomScroll() {
            return lostToDoomScroll;
        }

        public int getLostOther() {
            return lostOther;
        }
    }

    private static final int GAIN_CHAT = 0;
    private static final int GAIN_OTHER = 1;
    private static final int LOSS_DOOM = 2;
    private static final int LOSS_OTHER = 3;

    private final List<Sample> samples = new ArrayList<>();
    /** Local day → {gained from chat, gained other, lost to doom-scroll, lost other}. */
    private final TreeMap<Long, int[]> days = new TreeMap<>();

    /** Records the current HP without a change (e.g. on app start). Duplicates are skipped. */
    public void recordSample(long timestampMillis, int hp, int maxHp) {
        Sample last = samples.isEmpty() ? null : samples.get(samples.size() - 1);
        if (last != null && last.hp == hp && last.maxHp == maxHp) {
            return;
        }
        if (last != null && timestampMillis - last.timestampMillis < SAMPLE_SPACING_MILLIS
                && timestampMillis >= last.timestampMillis && samples.size() > 1) {
            // Keep the first sample of a burst at its time, holding the latest value.
            samples.set(samples.size() - 1, new Sample(last.timestampMillis, hp, maxHp));
        } else {
            samples.add(new Sample(timestampMillis, hp, maxHp));
        }
        prune(timestampMillis);
    }

    /** Records an HP change of {@code newHp - previousHp} caused by {@code reason}. */
    public void recordChange(long timestampMillis, int previousHp, int newHp, int maxHp,
                             @Nullable String reason, @NonNull TimeZone zone) {
        int delta = newHp - previousHp;
        if (delta != 0) {
            Long day = LocalDays.dayOf(timestampMillis, zone);
            int[] totals = days.get(day);
            if (totals == null) {
                totals = new int[4];
                days.put(day, totals);
            }
            Cause cause = Cause.fromReason(reason);
            if (delta > 0) {
                totals[cause == Cause.CHAT ? GAIN_CHAT : GAIN_OTHER] += delta;
            } else {
                totals[cause == Cause.DOOM_SCROLL ? LOSS_DOOM : LOSS_OTHER] -= delta;
            }
            while (days.size() > DAY_RETENTION) {
                days.pollFirstEntry();
            }
        }
        recordSample(timestampMillis, newHp, maxHp);
    }

    /**
     * @return the samples from {@code fromMillis} on, starting with the value HP had at
     * {@code fromMillis} (if known) so the line reaches the left edge.
     */
    @NonNull
    public List<Sample> samplesSince(long fromMillis) {
        List<Sample> result = new ArrayList<>();
        Sample before = null;
        for (Sample sample : samples) {
            if (sample.timestampMillis <= fromMillis) {
                before = sample;
            } else {
                result.add(sample);
            }
        }
        if (before != null) {
            result.add(0, new Sample(fromMillis, before.hp, before.maxHp));
        }
        return Collections.unmodifiableList(result);
    }

    /** @return the totals for local days {@code fromDay} to today, inclusive. */
    @NonNull
    public Totals totalsSince(long fromDay) {
        int[] sum = new int[4];
        for (Map.Entry<Long, int[]> entry : days.tailMap(fromDay, true).entrySet()) {
            for (int i = 0; i < sum.length; i++) {
                sum[i] += entry.getValue()[i];
            }
        }
        return new Totals(sum[GAIN_CHAT], sum[GAIN_OTHER], sum[LOSS_DOOM], sum[LOSS_OTHER]);
    }

    int sampleCount() {
        return samples.size();
    }

    private void prune(long nowMillis) {
        long cutoff = nowMillis - SAMPLE_RETENTION_MILLIS;
        // Keep the newest sample before the cutoff: it is the value at the start of the window.
        while (samples.size() > 1 && samples.get(1).timestampMillis <= cutoff) {
            samples.remove(0);
        }
        while (samples.size() > MAX_SAMPLES) {
            samples.remove(0);
        }
    }

    @NonNull
    public HpHistory copy() {
        HpHistory copy = new HpHistory();
        copy.samples.addAll(samples);
        for (Map.Entry<Long, int[]> entry : days.entrySet()) {
            copy.days.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONArray sampleArray = new JSONArray();
        for (Sample sample : samples) {
            sampleArray.put(new JSONArray().put(sample.timestampMillis).put(sample.hp)
                    .put(sample.maxHp));
        }
        JSONObject dayObject = new JSONObject();
        for (Map.Entry<Long, int[]> entry : days.entrySet()) {
            JSONArray totals = new JSONArray();
            for (int value : entry.getValue()) {
                totals.put(value);
            }
            dayObject.put(String.valueOf(entry.getKey()), totals);
        }
        return new JSONObject().put("samples", sampleArray).put("days", dayObject);
    }

    /** Reads {@link #toJson()} output, skipping malformed entries. */
    @NonNull
    public static HpHistory fromJson(@NonNull JSONObject json) {
        HpHistory history = new HpHistory();
        JSONArray sampleArray = json.optJSONArray("samples");
        if (sampleArray != null) {
            for (int i = 0; i < sampleArray.length(); i++) {
                JSONArray s = sampleArray.optJSONArray(i);
                if (s != null && s.length() == 3) {
                    history.samples.add(new Sample(s.optLong(0), s.optInt(1), s.optInt(2)));
                }
            }
        }
        JSONObject dayObject = json.optJSONObject("days");
        if (dayObject != null) {
            Iterator<String> keys = dayObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray totals = dayObject.optJSONArray(key);
                if (totals == null || totals.length() != 4) {
                    continue;
                }
                try {
                    int[] values = new int[4];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = totals.optInt(i);
                    }
                    history.days.put(Long.parseLong(key), values);
                } catch (NumberFormatException ignored) {
                    // skip a malformed day
                }
            }
        }
        return history;
    }
}
