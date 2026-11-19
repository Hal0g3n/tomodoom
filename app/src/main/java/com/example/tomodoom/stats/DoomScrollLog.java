package com.example.tomodoom.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Doom-scrolling history for the Stats page: counted time per local day and app, and sessions.
 *
 * <p>The monitor reports counted scrolling in chunks (a {@code DoomScrollEvent} per counted
 * minute, ending at its timestamp). A chunk starting more than {@link #SESSION_GAP_MILLIS} after
 * the previous one ended starts a new session. The gap is a little longer than the monitor's
 * 60-second away grace so poll jitter doesn't split a session.</p>
 *
 * <p>"Clean" time is time without doom-scrolling since tracking began: the gaps between
 * sessions, plus the gap before the first one and the open gap since the last one.</p>
 *
 * <p>Pure Java; not thread-safe ({@code StatsRepository} guards it with its lock).</p>
 */
public final class DoomScrollLog {

    static final long SESSION_GAP_MILLIS = TimeUnit.SECONDS.toMillis(120);
    static final int DAY_RETENTION = 30;
    static final long SESSION_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30);
    static final String UNKNOWN_PACKAGE = "unknown";

    /** One session of consecutive doom-scrolling. Immutable. */
    public static final class Session {
        private final long startMillis;
        private final long endMillis;

        public Session(long startMillis, long endMillis) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }

        public long getStartMillis() {
            return startMillis;
        }

        public long getEndMillis() {
            return endMillis;
        }

        public long getLengthMillis() {
            return endMillis - startMillis;
        }
    }

    /** Counted time for one app. Immutable. */
    public static final class AppTime {
        @NonNull
        private final String packageName;
        private final long millis;

        public AppTime(@NonNull String packageName, long millis) {
            this.packageName = packageName;
            this.millis = millis;
        }

        @NonNull
        public String getPackageName() {
            return packageName;
        }

        public long getMillis() {
            return millis;
        }
    }

    private final long trackingSinceMillis;
    /** Local day → package → counted millis. */
    private final TreeMap<Long, Map<String, Long>> days = new TreeMap<>();
    /** Oldest first. */
    private final List<Session> sessions = new ArrayList<>();
    /** Whether old sessions were pruned; then the gap before the oldest kept one is in the record. */
    private boolean pruned;
    /** Longest clean gap among sessions already pruned, so the record survives pruning. */
    private long prunedLongestCleanMillis;

    /** @param trackingSinceMillis when stats tracking began; clean time is counted from here. */
    public DoomScrollLog(long trackingSinceMillis) {
        this.trackingSinceMillis = trackingSinceMillis;
    }

    public long getTrackingSinceMillis() {
        return trackingSinceMillis;
    }

    /**
     * Records {@code durationMillis} of counted scrolling in {@code packageName}, ending at
     * {@code endMillis}.
     */
    public void record(@Nullable String packageName, long durationMillis, long endMillis,
                       @NonNull TimeZone zone) {
        long duration = Math.max(0, durationMillis);
        String pkg = packageName == null || packageName.isEmpty() ? UNKNOWN_PACKAGE : packageName;
        Long day = LocalDays.dayOf(endMillis, zone);
        Map<String, Long> apps = days.get(day);
        if (apps == null) {
            apps = new HashMap<>();
            days.put(day, apps);
        }
        Long previous = apps.get(pkg);
        apps.put(pkg, (previous == null ? 0L : previous) + duration);
        while (days.size() > DAY_RETENTION) {
            days.pollFirstEntry();
        }

        long start = Math.max(trackingSinceMillis, endMillis - duration);
        Session last = sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
        if (last != null && start - last.endMillis <= SESSION_GAP_MILLIS) {
            sessions.set(sessions.size() - 1, new Session(last.startMillis,
                    Math.max(last.endMillis, endMillis)));
        } else {
            sessions.add(new Session(start, endMillis));
        }
        pruneSessions(endMillis);
    }

    /** @return when doom-scrolling was last seen, or -1 if never. */
    public long getLastDoomScrollMillis() {
        return sessions.isEmpty() ? -1L : sessions.get(sessions.size() - 1).endMillis;
    }

    /** @return the current clean streak: time since the last doom-scroll (or since tracking began). */
    public long getCurrentCleanMillis(long nowMillis) {
        long last = getLastDoomScrollMillis();
        return Math.max(0, nowMillis - (last < 0 ? trackingSinceMillis : last));
    }

    /** @return the longest clean streak so far, including the current one. */
    public long getLongestCleanMillis(long nowMillis) {
        long longest = Math.max(prunedLongestCleanMillis, getCurrentCleanMillis(nowMillis));
        // Once pruned, the gap before the oldest kept session is already in the record.
        long previousEnd = pruned || sessions.isEmpty() ? -1L : trackingSinceMillis;
        for (Session session : sessions) {
            if (previousEnd >= 0) {
                longest = Math.max(longest, session.startMillis - previousEnd);
            }
            previousEnd = session.endMillis;
        }
        return longest;
    }

    /** @return the sessions that ended on or after {@code fromMillis}, oldest first. */
    @NonNull
    public List<Session> sessionsSince(long fromMillis) {
        List<Session> result = new ArrayList<>();
        for (Session session : sessions) {
            if (session.endMillis >= fromMillis) {
                result.add(session);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** @return the longest session that ended on or after {@code fromMillis}, in millis (0 if none). */
    public long getLongestSessionMillisSince(long fromMillis) {
        long longest = 0;
        for (Session session : sessionsSince(fromMillis)) {
            longest = Math.max(longest, session.getLengthMillis());
        }
        return longest;
    }

    /** @return counted millis on local day {@code day}, over every app. */
    public long getMillisOnDay(long day) {
        Map<String, Long> apps = days.get(day);
        long total = 0;
        if (apps != null) {
            for (Long millis : apps.values()) {
                total += millis;
            }
        }
        return total;
    }

    /** @return counted millis per app over local days {@code fromDay} to today, most first. */
    @NonNull
    public List<AppTime> getAppTimesSince(long fromDay) {
        Map<String, Long> sums = new HashMap<>();
        for (Map<String, Long> apps : days.tailMap(fromDay, true).values()) {
            for (Map.Entry<String, Long> entry : apps.entrySet()) {
                Long previous = sums.get(entry.getKey());
                sums.put(entry.getKey(), (previous == null ? 0L : previous) + entry.getValue());
            }
        }
        List<AppTime> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : sums.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(new AppTime(entry.getKey(), entry.getValue()));
            }
        }
        Collections.sort(result, (a, b) -> {
            int byTime = Long.compare(b.millis, a.millis);
            return byTime != 0 ? byTime : a.packageName.compareTo(b.packageName);
        });
        return Collections.unmodifiableList(result);
    }

    private void pruneSessions(long nowMillis) {
        long cutoff = nowMillis - SESSION_RETENTION_MILLIS;
        while (sessions.size() > 1 && sessions.get(0).endMillis < cutoff) {
            Session removed = sessions.remove(0);
            // Keep the clean gap that followed it, so the longest-streak record isn't lost.
            if (!pruned) {
                prunedLongestCleanMillis = removed.startMillis - trackingSinceMillis;
                pruned = true;
            }
            prunedLongestCleanMillis = Math.max(prunedLongestCleanMillis,
                    sessions.get(0).startMillis - removed.endMillis);
        }
    }

    @NonNull
    public DoomScrollLog copy() {
        DoomScrollLog copy = new DoomScrollLog(trackingSinceMillis);
        for (Map.Entry<Long, Map<String, Long>> entry : days.entrySet()) {
            copy.days.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        copy.sessions.addAll(sessions);
        copy.pruned = pruned;
        copy.prunedLongestCleanMillis = prunedLongestCleanMillis;
        return copy;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject dayObject = new JSONObject();
        for (Map.Entry<Long, Map<String, Long>> entry : days.entrySet()) {
            JSONObject apps = new JSONObject();
            for (Map.Entry<String, Long> app : entry.getValue().entrySet()) {
                apps.put(app.getKey(), app.getValue().longValue());
            }
            dayObject.put(String.valueOf(entry.getKey()), apps);
        }
        JSONArray sessionArray = new JSONArray();
        for (Session session : sessions) {
            sessionArray.put(new JSONArray().put(session.startMillis).put(session.endMillis));
        }
        return new JSONObject()
                .put("since", trackingSinceMillis)
                .put("days", dayObject)
                .put("sessions", sessionArray)
                .put("pruned", pruned)
                .put("prunedLongestClean", prunedLongestCleanMillis);
    }

    /**
     * Reads {@link #toJson()} output, skipping malformed entries.
     *
     * @param fallbackSinceMillis tracking start to use if the JSON has none.
     */
    @NonNull
    public static DoomScrollLog fromJson(@NonNull JSONObject json, long fallbackSinceMillis) {
        DoomScrollLog log = new DoomScrollLog(json.optLong("since", fallbackSinceMillis));
        JSONObject dayObject = json.optJSONObject("days");
        if (dayObject != null) {
            Iterator<String> keys = dayObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject apps = dayObject.optJSONObject(key);
                if (apps == null) {
                    continue;
                }
                long day;
                try {
                    day = Long.parseLong(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                Map<String, Long> map = new HashMap<>();
                Iterator<String> pkgs = apps.keys();
                while (pkgs.hasNext()) {
                    String pkg = pkgs.next();
                    map.put(pkg, apps.optLong(pkg));
                }
                log.days.put(day, map);
            }
        }
        JSONArray sessionArray = json.optJSONArray("sessions");
        if (sessionArray != null) {
            for (int i = 0; i < sessionArray.length(); i++) {
                JSONArray s = sessionArray.optJSONArray(i);
                if (s != null && s.length() == 2) {
                    log.sessions.add(new Session(s.optLong(0), s.optLong(1)));
                }
            }
        }
        log.pruned = json.optBoolean("pruned", false);
        log.prunedLongestCleanMillis = json.optLong("prunedLongestClean", 0);
        return log;
    }
}
