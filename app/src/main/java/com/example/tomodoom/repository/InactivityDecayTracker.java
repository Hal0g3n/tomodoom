package com.example.tomodoom.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Lowers the pet's HP when the user comes back after time away from the app
 * ({@link InactivityDecayPolicy}). The decay is worked out all at once on return, so nothing runs
 * in the background and it survives process death and reboots.
 *
 * <p>The time the app went to the background is kept in its own prefs file
 * {@value #PREFS_NAME} (key {@value #KEY_AWAY_SINCE}, wall-clock millis). HP only changes through
 * {@link PetStateRepository#decreaseHp}, with the reason {@value #REASON}.</p>
 *
 * <p>Thread-safe. {@code TomodoomApplication} calls it when the started-activity count goes
 * 1 → 0 and 0 → 1, the same signal the chat uses (CLAUDE.md §3.3).</p>
 */
public final class InactivityDecayTracker {

    private static final String TAG = "InactivityDecay";

    public static final String PREFS_NAME = "tomodoom_decay";
    static final String KEY_AWAY_SINCE = "away_since_ms";
    public static final String REASON = "inactivity_decay";

    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final PetStateRepository petStateRepository;
    /** HP lost to the last absence, until the Pets page shows it; 0 = nothing to show. */
    @NonNull
    private final MutableLiveData<Integer> pendingNotice = new MutableLiveData<>(0);

    public InactivityDecayTracker(@NonNull Context context,
                                  @NonNull PetStateRepository petStateRepository) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.petStateRepository = petStateRepository;
    }

    /** The app has just gone to the background. */
    @AnyThread
    public synchronized void onAppBackgrounded(long nowWallMs) {
        prefs.edit().putLong(KEY_AWAY_SINCE, nowWallMs).apply();
    }

    /**
     * The app has come back to the foreground: applies the decay for the time away.
     *
     * @return the HP removed (0 when there was no recorded absence, onboarding isn't done, or the
     * absence was within the grace period).
     */
    @AnyThread
    public synchronized int onAppForegrounded(long nowWallMs) {
        if (!prefs.contains(KEY_AWAY_SINCE)) {
            return 0; // first launch, or the process died while visible
        }
        long awaySince = prefs.getLong(KEY_AWAY_SINCE, nowWallMs);
        prefs.edit().remove(KEY_AWAY_SINCE).apply();
        if (!petStateRepository.isOnboardingComplete()) {
            return 0;
        }
        long awayMs = nowWallMs - awaySince;
        int loss = InactivityDecayPolicy.hpLoss(
                petStateRepository.getCurrentState().getCurrentHp(), awayMs);
        if (loss <= 0) {
            return 0;
        }
        Log.i(TAG, "away " + (awayMs / 60000L) + " min: -" + loss + " HP");
        petStateRepository.decreaseHp(loss, REASON);
        pendingNotice.postValue(loss);
        return loss;
    }

    /** HP lost to the last absence while the user hasn't seen it yet; 0 when there's none. */
    @NonNull
    public LiveData<Integer> getPendingNotice() {
        return pendingNotice;
    }

    /** The notice was shown. */
    @AnyThread
    public synchronized void acknowledgeNotice() {
        pendingNotice.postValue(0);
    }
}
