package com.example.tomodoom.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Fake but plausible Stats history for demos and screenshots (the debug panel's "Fill Stats with
 * demo data"). Builds a week of doom-scroll sessions in social apps, quiz passes with their chat
 * turns' tokens, and the HP line those events imply, ending at the pet's real HP so the Stats page
 * agrees with the Pets page. Deterministic for a given seed. Pure Java.
 */
public final class DemoStats {

    static final int DAYS = StatsReport.WEEK_DAYS;
    /** No doom-scrolling in the last two hours, so the clean-streak timer shows something. */
    static final long QUIET_BEFORE_NOW_MILLIS = TimeUnit.HOURS.toMillis(2);

    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final int QUIZ_HP = 10;
    private static final int DECAY_HP = 8;

    /** Watched-app package IDs, the heaviest first (weights below). */
    private static final String[] APPS = {
            "com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube",
            "com.reddit.frontpage", "com.twitter.android",
    };
    private static final int[] APP_WEIGHTS = {35, 30, 20, 10, 5};
    private static final String[] MODELS = {
            "gemini-3.1-flash-lite", "gemini-flash-lite-latest", "gemini-3.5-flash",
    };
    private static final int[] MODEL_WEIGHTS = {80, 15, 5};

    private DemoStats() {
    }

    /**
     * @param nowMillis the current time; nothing is generated after it.
     * @param currentHp the pet's HP now: the generated line ends here.
     */
    @NonNull
    public static StatsSnapshot generate(long nowMillis, @NonNull TimeZone zone, int currentHp,
                                         int maxHp, long seed) {
        Random random = new Random(seed);
        long today = LocalDays.dayOf(nowMillis, zone);
        long since = LocalDays.startOf(today - (DAYS - 1), zone);
        long lastDoom = nowMillis - QUIET_BEFORE_NOW_MILLIS;
        int cleanDay = random.nextInt(DAYS - 1); // one day with no doom-scrolling at all

        TokenUsageLedger tokens = new TokenUsageLedger();
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            long dayStart = LocalDays.startOf(today - (DAYS - 1) + i, zone);
            if (i != cleanDay) {
                addSessions(random, dayStart, lastDoom, events);
            }
            addChat(random, dayStart, nowMillis, tokens, events);
            long decayTime = dayStart + 8 * HOUR + random.nextInt(60) * MINUTE;
            if (i > 0 && random.nextInt(3) == 0 && decayTime <= nowMillis) {
                // A morning away from the app: the inactivity decay.
                events.add(new Event(decayTime, -DECAY_HP, "inactivity_decay", null));
            }
        }
        Collections.sort(events, (a, b) -> Long.compare(a.time, b.time));

        // Walk back from the real HP, clamping, so the line ends exactly at currentHp.
        int[] before = new int[events.size()];
        int hp = clamp(currentHp, maxHp);
        for (int i = events.size() - 1; i >= 0; i--) {
            before[i] = clamp(hp - events.get(i).delta, maxHp);
            hp = before[i];
        }

        HpHistory history = new HpHistory();
        DoomScrollLog doomScroll = new DoomScrollLog(since);
        history.recordSample(since, hp, maxHp);
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            int after = i + 1 < events.size() ? before[i + 1] : clamp(currentHp, maxHp);
            if (event.app != null) {
                doomScroll.record(event.app, MINUTE, event.time, zone);
            }
            history.recordChange(event.time, before[i], after, maxHp, event.reason, zone);
        }
        history.recordSample(nowMillis, clamp(currentHp, maxHp), maxHp);
        return new StatsSnapshot(tokens, history, doomScroll);
    }

    /** 1–4 sessions of 3–25 counted minutes, between 07:00 and midnight, one event per minute. */
    private static void addSessions(@NonNull Random random, long dayStart, long latestEnd,
                                    @NonNull List<Event> events) {
        int count = 1 + random.nextInt(4);
        List<Long> starts = new ArrayList<>();
        for (int s = 0; s < count; s++) {
            starts.add(dayStart + 7 * HOUR + random.nextInt(17 * 60) * MINUTE);
        }
        Collections.sort(starts);
        long previousEnd = Long.MIN_VALUE;
        for (long start : starts) {
            int minutes = 3 + random.nextInt(23);
            long end = start + minutes * MINUTE;
            if (start < previousEnd + 15 * MINUTE || end > latestEnd) {
                continue;
            }
            String app = pick(random, APPS, APP_WEIGHTS);
            for (int m = 1; m <= minutes; m++) {
                events.add(new Event(start + m * MINUTE, -1, "doom_scroll:" + app, app));
            }
            previousEnd = end;
        }
    }

    /** 1–4 quizzes a day, each after 2–4 chat turns, plus a few ungraded turns. */
    private static void addChat(@NonNull Random random, long dayStart, long nowMillis,
                                @NonNull TokenUsageLedger tokens, @NonNull List<Event> events) {
        int quizzes = 1 + random.nextInt(4);
        for (int q = 0; q < quizzes; q++) {
            long time = dayStart + 9 * HOUR + random.nextInt(13 * 60) * MINUTE;
            if (time > nowMillis) {
                continue;
            }
            int turns = 2 + random.nextInt(3);
            for (int t = 0; t < turns; t++) {
                recordTurn(random, tokens);
            }
            events.add(new Event(time, QUIZ_HP, "quiz_passed:demo-" + dayStart + "-" + q, null));
        }
        int chatter = random.nextInt(4);
        for (int t = 0; t < chatter; t++) {
            recordTurn(random, tokens);
        }
    }

    private static void recordTurn(@NonNull Random random, @NonNull TokenUsageLedger tokens) {
        long prompt = 700 + random.nextInt(900);
        long completion = 40 + random.nextInt(180);
        tokens.record(pick(random, MODELS, MODEL_WEIGHTS), prompt, completion,
                prompt + completion);
    }

    @NonNull
    private static String pick(@NonNull Random random, @NonNull String[] values,
                               @NonNull int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < values.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    private static int clamp(int hp, int maxHp) {
        return Math.max(0, Math.min(hp, maxHp));
    }

    /** One HP change; {@code app} is set for a counted doom-scroll minute. */
    private static final class Event {
        final long time;
        final int delta;
        @NonNull
        final String reason;
        @Nullable
        final String app;

        Event(long time, int delta, @NonNull String reason, @Nullable String app) {
            this.time = time;
            this.delta = delta;
            this.reason = reason;
            this.app = app;
        }
    }
}
