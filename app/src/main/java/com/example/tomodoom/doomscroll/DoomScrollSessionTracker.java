package com.example.tomodoom.doomscroll;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.model.DoomScrollEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks foreground intervals and applies the duration threshold and per-app cooldown. */
final class DoomScrollSessionTracker {

    @NonNull
    private final Set<String> monitoredPackages;
    @NonNull
    private final Map<String, Long> lastReportedByPackage = new HashMap<>();
    private final long minimumSessionMillis;
    private final long packageCooldownMillis;
    @Nullable
    private String foregroundPackage;
    private long foregroundSinceMillis;
    private boolean sessionReported;

    DoomScrollSessionTracker(@NonNull Set<String> monitoredPackages,
                             long minimumSessionMillis,
                             long packageCooldownMillis) {
        this.monitoredPackages = new HashSet<>(monitoredPackages);
        this.minimumSessionMillis = minimumSessionMillis;
        this.packageCooldownMillis = packageCooldownMillis;
    }

    @Nullable
    DoomScrollEvent onForeground(@NonNull String packageName, long timestampMillis) {
        if (packageName.equals(foregroundPackage)) {
            return null;
        }
        DoomScrollEvent event = reportIfEligible(timestampMillis);
        foregroundPackage = packageName;
        foregroundSinceMillis = timestampMillis;
        sessionReported = false;
        return event;
    }

    @Nullable
    DoomScrollEvent onBackground(@NonNull String packageName, long timestampMillis) {
        if (!packageName.equals(foregroundPackage)) {
            return null;
        }
        DoomScrollEvent event = reportIfEligible(timestampMillis);
        resetCurrentSession();
        return event;
    }

    @Nullable
    DoomScrollEvent onPoll(long timestampMillis) {
        return reportIfEligible(timestampMillis);
    }

    void resetCurrentSession() {
        foregroundPackage = null;
        foregroundSinceMillis = 0L;
        sessionReported = false;
    }

    @Nullable
    private DoomScrollEvent reportIfEligible(long timestampMillis) {
        String packageName = foregroundPackage;
        if (packageName == null
                || sessionReported
                || !monitoredPackages.contains(packageName)) {
            return null;
        }
        long durationMillis = Math.max(0L, timestampMillis - foregroundSinceMillis);
        if (durationMillis < minimumSessionMillis) {
            return null;
        }
        sessionReported = true;
        Long lastReported = lastReportedByPackage.get(packageName);
        if (lastReported != null && timestampMillis - lastReported < packageCooldownMillis) {
            return null;
        }
        lastReportedByPackage.put(packageName, timestampMillis);
        return new DoomScrollEvent(
                packageName, durationMillis, DoomScrollEvent.DEFAULT_HP_PENALTY, timestampMillis);
    }
}
