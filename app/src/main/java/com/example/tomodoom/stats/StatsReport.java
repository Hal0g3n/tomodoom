package com.example.tomodoom.stats;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Everything the Stats page shows, computed from a {@link StatsSnapshot} at a given time and time
 * zone. Pure Java and immutable, so the page only formats numbers.
 */
public final class StatsReport {

    /** Days in the weekly views (today and the 6 days before it). */
    public static final int WEEK_DAYS = 7;
    public static final long DAY_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(1);
    public static final long WEEK_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(WEEK_DAYS);

    private final long nowMillis;
    private final long trackingSinceMillis;
    private final long lastDoomScrollMillis;
    private final long currentCleanMillis;
    private final long longestCleanMillis;
    private final long todayMillis;
    private final int sessionsToday;
    private final long longestSessionWeekMillis;
    @NonNull
    private final long[] weekDayStarts;
    @NonNull
    private final long[] weekDayMillis;
    @NonNull
    private final List<DoomScrollLog.AppTime> weekApps;
    @NonNull
    private final List<HpHistory.Sample> hpDay;
    @NonNull
    private final List<HpHistory.Sample> hpWeek;
    @NonNull
    private final HpHistory.Totals hpWeekTotals;
    @NonNull
    private final List<TokenUsageLedger.ModelUsage> tokenUsage;
    @NonNull
    private final TokenUsageLedger.ModelUsage tokenTotal;

    private StatsReport(@NonNull StatsSnapshot snapshot, long nowMillis, @NonNull TimeZone zone) {
        DoomScrollLog doom = snapshot.getDoomScroll();
        HpHistory hp = snapshot.getHp();
        long today = LocalDays.dayOf(nowMillis, zone);
        long firstWeekDay = today - (WEEK_DAYS - 1);
        long todayStart = LocalDays.startOf(today, zone);
        long weekStart = LocalDays.startOf(firstWeekDay, zone);

        this.nowMillis = nowMillis;
        this.trackingSinceMillis = doom.getTrackingSinceMillis();
        this.lastDoomScrollMillis = doom.getLastDoomScrollMillis();
        this.currentCleanMillis = doom.getCurrentCleanMillis(nowMillis);
        this.longestCleanMillis = doom.getLongestCleanMillis(nowMillis);
        this.todayMillis = doom.getMillisOnDay(today);
        this.sessionsToday = doom.sessionsSince(todayStart).size();
        this.longestSessionWeekMillis = doom.getLongestSessionMillisSince(weekStart);
        this.weekDayStarts = new long[WEEK_DAYS];
        this.weekDayMillis = new long[WEEK_DAYS];
        for (int i = 0; i < WEEK_DAYS; i++) {
            weekDayStarts[i] = LocalDays.startOf(firstWeekDay + i, zone);
            weekDayMillis[i] = doom.getMillisOnDay(firstWeekDay + i);
        }
        this.weekApps = doom.getAppTimesSince(firstWeekDay);
        this.hpDay = hp.samplesSince(nowMillis - DAY_WINDOW_MILLIS);
        this.hpWeek = hp.samplesSince(nowMillis - WEEK_WINDOW_MILLIS);
        this.hpWeekTotals = hp.totalsSince(firstWeekDay);
        this.tokenUsage = snapshot.getTokens().getUsage();
        this.tokenTotal = snapshot.getTokens().getGrandTotal();
    }

    @NonNull
    public static StatsReport of(@NonNull StatsSnapshot snapshot, long nowMillis,
                                 @NonNull TimeZone zone) {
        return new StatsReport(snapshot, nowMillis, zone);
    }

    public long getNowMillis() {
        return nowMillis;
    }

    public long getTrackingSinceMillis() {
        return trackingSinceMillis;
    }

    public boolean hasDoomScrolled() {
        return lastDoomScrollMillis >= 0;
    }

    /** @return when doom-scrolling was last seen, or -1 if never. */
    public long getLastDoomScrollMillis() {
        return lastDoomScrollMillis;
    }

    /** @return time since the last doom-scroll, or since tracking began if there was none. */
    public long getCurrentCleanMillis() {
        return currentCleanMillis;
    }

    public long getLongestCleanMillis() {
        return longestCleanMillis;
    }

    public long getTodayMillis() {
        return todayMillis;
    }

    public int getSessionsToday() {
        return sessionsToday;
    }

    public long getLongestSessionWeekMillis() {
        return longestSessionWeekMillis;
    }

    /** @return the start of each of the last {@link #WEEK_DAYS} local days, oldest first. */
    @NonNull
    public long[] getWeekDayStarts() {
        return weekDayStarts.clone();
    }

    /** @return counted doom-scrolling per day, matching {@link #getWeekDayStarts()}. */
    @NonNull
    public long[] getWeekDayMillis() {
        return weekDayMillis.clone();
    }

    public long getWeekTotalMillis() {
        long total = 0;
        for (long millis : weekDayMillis) {
            total += millis;
        }
        return total;
    }

    /** @return counted doom-scrolling per app over the last {@link #WEEK_DAYS} days, most first. */
    @NonNull
    public List<DoomScrollLog.AppTime> getWeekApps() {
        return weekApps;
    }

    /** @return the HP line over the last 24 hours (step samples, oldest first). */
    @NonNull
    public List<HpHistory.Sample> getHpDay() {
        return hpDay;
    }

    /** @return the HP line over the last 7 days (step samples, oldest first). */
    @NonNull
    public List<HpHistory.Sample> getHpWeek() {
        return hpWeek;
    }

    @NonNull
    public HpHistory.Totals getHpWeekTotals() {
        return hpWeekTotals;
    }

    /** @return tokens per model, most first; empty if no LLM request has succeeded. */
    @NonNull
    public List<TokenUsageLedger.ModelUsage> getTokenUsage() {
        return tokenUsage;
    }

    @NonNull
    public TokenUsageLedger.ModelUsage getTokenTotal() {
        return tokenTotal;
    }
}
