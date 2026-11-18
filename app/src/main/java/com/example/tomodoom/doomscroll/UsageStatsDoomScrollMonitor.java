package com.example.tomodoom.doomscroll;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.model.DoomScrollEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Detects long foreground sessions in common social/video apps using Android Usage Access.
 * Polls every 15 seconds while the app process is alive; an uninterrupted 15-minute session
 * emits one penalty, with a 30-minute per-app cooldown for closely spaced sessions.
 */
public final class UsageStatsDoomScrollMonitor implements DoomScrollMonitor {

    private static final String TAG = "UsageStatsDoomScrollMonitor";
    private static final long POLL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long MIN_SESSION_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final long PACKAGE_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long EVENT_LOOKBACK_MILLIS = TimeUnit.MINUTES.toMillis(30);
    @SuppressWarnings("deprecation")
    private static final int EVENT_FOREGROUND = UsageEvents.Event.MOVE_TO_FOREGROUND;
    @SuppressWarnings("deprecation")
    private static final int EVENT_BACKGROUND = UsageEvents.Event.MOVE_TO_BACKGROUND;
    private static final Set<String> DOOM_SCROLL_PACKAGES = Collections.unmodifiableSet(
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

    @NonNull
    private final Context appContext;
    @Nullable
    private final UsageStatsManager usageStatsManager;
    @NonNull
    private final Object lock = new Object();
    @NonNull
    private final DoomScrollSessionTracker sessionTracker;
    @Nullable
    private volatile DoomScrollListener listener;
    @Nullable
    private ScheduledExecutorService executor;
    @Nullable
    private ScheduledFuture<?> pollingTask;
    private long lastQueryMillis;
    private boolean monitoring;

    public UsageStatsDoomScrollMonitor(@NonNull Context context) {
        appContext = context.getApplicationContext();
        usageStatsManager = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        sessionTracker = new DoomScrollSessionTracker(
                DOOM_SCROLL_PACKAGES, MIN_SESSION_MILLIS, PACKAGE_COOLDOWN_MILLIS);
    }

    @AnyThread
    @Override
    public void startMonitoring() {
        if (!isPermissionGranted()) {
            Log.i(TAG, "Usage Access not granted; monitoring was not started");
            return;
        }
        synchronized (lock) {
            if (monitoring) {
                return;
            }
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "doom-scroll-monitor");
                thread.setDaemon(true);
                return thread;
            });
            lastQueryMillis = Math.max(0L, System.currentTimeMillis() - EVENT_LOOKBACK_MILLIS);
            monitoring = true;
            pollingTask = executor.scheduleWithFixedDelay(
                    this::pollSafely, 0L, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    @AnyThread
    @Override
    public void stopMonitoring() {
        synchronized (lock) {
            monitoring = false;
            if (pollingTask != null) {
                pollingTask.cancel(true);
                pollingTask = null;
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            sessionTracker.resetCurrentSession();
            lastQueryMillis = 0L;
        }
    }

    @AnyThread
    @Override
    public boolean isMonitoring() {
        synchronized (lock) {
            return monitoring;
        }
    }

    @AnyThread
    @SuppressWarnings("deprecation")
    @Override
    public boolean isPermissionGranted() {
        AppOpsManager appOps = (AppOpsManager) appContext.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null || usageStatsManager == null) {
            return false;
        }
        int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.getPackageName())
                : appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @AnyThread
    @Override
    public void setListener(@Nullable DoomScrollListener listener) {
        this.listener = listener;
    }

    private void pollSafely() {
        try {
            pollUsageEvents();
        } catch (SecurityException exception) {
            Log.w(TAG, "Usage Access was revoked; stopping doom-scroll monitoring", exception);
            stopMonitoring();
        }
    }

    private void pollUsageEvents() {
        if (usageStatsManager == null) {
            Log.e(TAG, "UsageStatsManager is unavailable");
            stopMonitoring();
            return;
        }
        if (!isPermissionGranted()) {
            Log.i(TAG, "Usage Access was revoked; stopping doom-scroll monitoring");
            stopMonitoring();
            return;
        }
        long begin;
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            begin = Math.max(0L, lastQueryMillis - 1L);
        }
        long now = System.currentTimeMillis();
        UsageEvents events = usageStatsManager.queryEvents(begin, now);
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            lastQueryMillis = now;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            handleUsageEvent(event);
        }
        maybeReportLongSession(now);
    }

    private void handleUsageEvent(@NonNull UsageEvents.Event event) {
        int type = event.getEventType();
        String packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }
        DoomScrollEvent detectedEvent;
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            if (type == EVENT_FOREGROUND) {
                detectedEvent = sessionTracker.onForeground(packageName, event.getTimeStamp());
            } else if (type == EVENT_BACKGROUND) {
                detectedEvent = sessionTracker.onBackground(packageName, event.getTimeStamp());
            } else {
                return;
            }
        }
        notifyListener(detectedEvent);
    }

    private void maybeReportLongSession(long nowMillis) {
        DoomScrollEvent detectedEvent;
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            detectedEvent = sessionTracker.onPoll(nowMillis);
        }
        notifyListener(detectedEvent);
    }

    private void notifyListener(@Nullable DoomScrollEvent event) {
        if (event == null) {
            return;
        }
        DoomScrollListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onDoomScrollDetected(event);
        }
    }
}
