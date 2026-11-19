package com.example.tomodoom.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tomodoom.model.WatchedApps;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The apps doom-scroll detection watches, as chosen by the user. Starts with
 * {@link WatchedApps#DEFAULT_PACKAGES}. Follows the same rules as {@link PetStateRepository}
 * (CLAUDE.md §3.2): one instance in {@code AppContainer}, the only reader/writer of the
 * {@value #PREFS_NAME} prefs file, {@code synchronized} mutators callable from any thread,
 * publishing only with {@code postValue} under the lock, and {@code apply()} writes.
 */
public final class WatchedAppsRepository {

    private static final String TAG = "WatchedAppsRepository";

    public static final String PREFS_NAME = "tomodoom_doomscroll";
    static final String KEY_WATCHED_PACKAGES = "watched_packages";

    private final Object lock = new Object();
    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final MutableLiveData<Set<String>> watchedLiveData;
    /** Latest unmodifiable snapshot; written only under {@link #lock}. */
    @NonNull
    private volatile Set<String> current;

    public WatchedAppsRepository(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_WATCHED_PACKAGES, null);
        // Copy: the set SharedPreferences returns must never be modified or kept.
        current = stored == null
                ? WatchedApps.DEFAULT_PACKAGES
                : Collections.unmodifiableSet(new HashSet<>(stored));
        watchedLiveData = new MutableLiveData<>(current);
    }

    /** Observe from the UI. Emissions are delivered on the main thread. */
    @NonNull
    public LiveData<Set<String>> getWatchedPackages() {
        return watchedLiveData;
    }

    /** @return the latest unmodifiable snapshot, from any thread. */
    @AnyThread
    @NonNull
    public Set<String> getCurrent() {
        return current;
    }

    /** Adds or removes one package from the watched set and saves it. */
    @AnyThread
    @NonNull
    public Set<String> setWatched(@NonNull String packageName, boolean watched) {
        synchronized (lock) {
            Set<String> updated = WatchedApps.with(current, packageName, watched);
            if (updated.equals(current)) {
                return current;
            }
            current = updated;
            prefs.edit().putStringSet(KEY_WATCHED_PACKAGES, new HashSet<>(updated)).apply();
            watchedLiveData.postValue(updated);
            Log.i(TAG, "Watched apps: " + updated.size());
            return updated;
        }
    }
}
