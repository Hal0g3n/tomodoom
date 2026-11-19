package com.example.tomodoom.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tomodoom.model.DoomScrollEvent;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.stats.DoomScrollLog;
import com.example.tomodoom.stats.HpHistory;
import com.example.tomodoom.stats.StatsSnapshot;
import com.example.tomodoom.stats.TokenUsageLedger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimeZone;

/**
 * Holds the Stats page's history: tokens per model, HP over time and doom-scrolling. Backed by its
 * own prefs file {@value #PREFS_NAME} and published as {@link LiveData} of {@link StatsSnapshot}.
 *
 * <p>Same rules as {@link PetStateRepository} (CLAUDE.md §3.2): one instance (in
 * {@code AppContainer}), recorders are {@code synchronized} and callable from any thread,
 * publishing uses only {@code postValue} under the lock, and writes use {@code apply()}.
 * {@link #recordHpChange} is called while {@link PetStateRepository}'s lock is held, so this class
 * never calls into {@link PetStateRepository} after construction.</p>
 *
 * <p>Tracking starts the first time this class runs; nothing earlier is known.</p>
 */
public final class StatsRepository {

    private static final String TAG = "StatsRepository";

    public static final String PREFS_NAME = "tomodoom_stats";
    static final String KEY_TRACKING_SINCE = "tracking_since";
    static final String KEY_TOKEN_USAGE = "token_usage";
    static final String KEY_HP_HISTORY = "hp_history";
    static final String KEY_DOOM_SCROLL_LOG = "doom_scroll_log";

    private final Object lock = new Object();
    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final TokenUsageLedger tokens;
    @NonNull
    private final HpHistory hp;
    @NonNull
    private final DoomScrollLog doomScroll;
    @NonNull
    private final MutableLiveData<StatsSnapshot> stats;
    @NonNull
    private volatile StatsSnapshot current;

    /**
     * Loads the saved history (a small file read) and records the pet's current HP.
     *
     * @param initialPet the pet right now, from {@link PetStateRepository#getCurrentState()}.
     */
    public StatsRepository(@NonNull Context context, @NonNull PetState initialPet) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long since = prefs.getLong(KEY_TRACKING_SINCE, -1L);
        if (since < 0) {
            since = now;
            prefs.edit().putLong(KEY_TRACKING_SINCE, since).apply();
        }
        tokens = readTokens(prefs);
        hp = readHp(prefs);
        doomScroll = readDoomScroll(prefs, since);
        hp.recordSample(now, initialPet.getCurrentHp(), initialPet.getMaxHp());
        saveHp();
        current = snapshot();
        stats = new MutableLiveData<>(current);
    }

    /** Observe from the UI; emissions arrive on the main thread. */
    @NonNull
    public LiveData<StatsSnapshot> getStats() {
        return stats;
    }

    /** @return the latest snapshot, from any thread. */
    @AnyThread
    @NonNull
    public StatsSnapshot getCurrentStats() {
        return current;
    }

    /** Records one LLM request's tokens. Matches {@code TokenUsageListener}. */
    @AnyThread
    public void recordTokens(@NonNull String model, long promptTokens, long completionTokens,
                             long totalTokens) {
        synchronized (lock) {
            tokens.record(model, promptTokens, completionTokens, totalTokens);
            try {
                prefs.edit().putString(KEY_TOKEN_USAGE, tokens.toJson().toString()).apply();
            } catch (JSONException e) {
                Log.w(TAG, "couldn't save token usage", e);
            }
            publishLocked();
        }
    }

    /** Records an HP change. Matches {@code PetStateRepository.HpChangeListener}. */
    @AnyThread
    public void recordHpChange(@NonNull PetState previous, @NonNull PetState next,
                               @NonNull String reason) {
        synchronized (lock) {
            hp.recordChange(System.currentTimeMillis(), previous.getCurrentHp(),
                    next.getCurrentHp(), next.getMaxHp(), reason, TimeZone.getDefault());
            saveHp();
            publishLocked();
        }
    }

    /** Records detected doom-scrolling. */
    @AnyThread
    public void recordDoomScroll(@NonNull DoomScrollEvent event) {
        synchronized (lock) {
            doomScroll.record(event.getSourcePackage(), event.getDurationMillis(),
                    event.getTimestampMillis(), TimeZone.getDefault());
            try {
                prefs.edit().putString(KEY_DOOM_SCROLL_LOG, doomScroll.toJson().toString())
                        .apply();
            } catch (JSONException e) {
                Log.w(TAG, "couldn't save doom-scroll log", e);
            }
            publishLocked();
        }
    }

    // Internals. Every method ending in "Locked" (and saveHp) runs while holding `lock`, except
    // during construction.

    private void saveHp() {
        try {
            prefs.edit().putString(KEY_HP_HISTORY, hp.toJson().toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "couldn't save HP history", e);
        }
    }

    private void publishLocked() {
        current = snapshot();
        stats.postValue(current);
    }

    @NonNull
    private StatsSnapshot snapshot() {
        return new StatsSnapshot(tokens.copy(), hp.copy(), doomScroll.copy());
    }

    @NonNull
    private static TokenUsageLedger readTokens(@NonNull SharedPreferences prefs) {
        String json = prefs.getString(KEY_TOKEN_USAGE, null);
        try {
            return json == null ? new TokenUsageLedger()
                    : TokenUsageLedger.fromJson(new JSONArray(json));
        } catch (JSONException e) {
            Log.w(TAG, "discarding unreadable token usage", e);
            return new TokenUsageLedger();
        }
    }

    @NonNull
    private static HpHistory readHp(@NonNull SharedPreferences prefs) {
        JSONObject json = readObject(prefs, KEY_HP_HISTORY);
        return json == null ? new HpHistory() : HpHistory.fromJson(json);
    }

    @NonNull
    private static DoomScrollLog readDoomScroll(@NonNull SharedPreferences prefs, long since) {
        JSONObject json = readObject(prefs, KEY_DOOM_SCROLL_LOG);
        return json == null ? new DoomScrollLog(since) : DoomScrollLog.fromJson(json, since);
    }

    @Nullable
    private static JSONObject readObject(@NonNull SharedPreferences prefs, @NonNull String key) {
        String json = prefs.getString(key, null);
        if (json == null) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            Log.w(TAG, "discarding unreadable " + key, e);
            return null;
        }
    }
}
