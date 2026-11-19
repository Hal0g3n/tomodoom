package com.example.tomodoom.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The apps doom-scroll detection watches. Only time in a watched app can cost HP. The user picks
 * them in the watched-apps picker; {@link #DEFAULT_PACKAGES} is the starting selection.
 */
public final class WatchedApps {

    /** Instagram, TikTok (two package names), Reddit, YouTube, X/Twitter, Facebook, Snapchat. */
    @NonNull
    public static final Set<String> DEFAULT_PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.instagram.android",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                    "com.reddit.frontpage",
                    "com.google.android.youtube",
                    "com.twitter.android",
                    "com.facebook.katana",
                    "com.snapchat.android"
            )));

    private WatchedApps() {
    }

    /** @return a new unmodifiable copy of {@code watched} with {@code packageName} added or removed. */
    @NonNull
    public static Set<String> with(@NonNull Set<String> watched, @NonNull String packageName,
                                   boolean isWatched) {
        Set<String> copy = new HashSet<>(watched);
        if (isWatched) {
            copy.add(packageName);
        } else {
            copy.remove(packageName);
        }
        return Collections.unmodifiableSet(copy);
    }

    /** @return how many of {@code apps} are in {@code watched}. */
    public static int countWatched(@NonNull List<InstalledApp> apps, @NonNull Set<String> watched) {
        int count = 0;
        for (InstalledApp app : apps) {
            if (watched.contains(app.getPackageName())) {
                count++;
            }
        }
        return count;
    }

    /** @return a new list: watched apps first, each group by label (case-insensitive), then ID. */
    @NonNull
    public static List<InstalledApp> sortForDisplay(@NonNull List<InstalledApp> apps,
                                                    @NonNull final Set<String> watched) {
        List<InstalledApp> sorted = new ArrayList<>(apps);
        Collections.sort(sorted, new Comparator<InstalledApp>() {
            @Override
            public int compare(InstalledApp a, InstalledApp b) {
                boolean aWatched = watched.contains(a.getPackageName());
                boolean bWatched = watched.contains(b.getPackageName());
                if (aWatched != bWatched) {
                    return aWatched ? -1 : 1;
                }
                int byLabel = a.getLabel().compareToIgnoreCase(b.getLabel());
                return byLabel != 0 ? byLabel : a.getPackageName().compareTo(b.getPackageName());
            }
        });
        return sorted;
    }
}
