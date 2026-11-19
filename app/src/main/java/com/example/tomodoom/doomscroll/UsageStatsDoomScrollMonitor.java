package com.example.tomodoom.doomscroll;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.view.Display;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.model.DoomScrollEvent;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Detects doom-scrolling in the apps the user watches using Android Usage Access. Polls every
 * 15 seconds and charges 1 HP per minute a watched app is on screen in portrait. The watched set
 * is read at every poll, so a change in the picker applies within one poll. Landscape (such
 * as fullscreen video) doesn't count. {@link DoomScrollSessionTracker} holds the session rules.
 */
public final class UsageStatsDoomScrollMonitor implements DoomScrollMonitor {

    private static final String TAG = "UsageStatsDoomScrollMonitor";
    private static final long POLL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long MAX_POLL_GAP_MILLIS = TimeUnit.SECONDS.toMillis(45);
    private static final long PENALTY_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final int HP_PER_PENALTY_INTERVAL = 1;
    private static final long WARNING_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long AWAY_GRACE_MILLIS = TimeUnit.SECONDS.toMillis(60);
    private static final long EVENT_LOOKBACK_MILLIS = TimeUnit.MINUTES.toMillis(30);
    @SuppressWarnings("deprecation")
    private static final int EVENT_FOREGROUND = UsageEvents.Event.MOVE_TO_FOREGROUND;
    @SuppressWarnings("deprecation")
    private static final int EVENT_BACKGROUND = UsageEvents.Event.MOVE_TO_BACKGROUND;
    @NonNull
    private final Context appContext;
    @Nullable
    private final UsageStatsManager usageStatsManager;
    @Nullable
    private final DisplayManager displayManager;
    @Nullable
    private final PowerManager powerManager;
    @NonNull
    private final Object lock = new Object();
    @NonNull
    private final DoomScrollSessionTracker sessionTracker;
    @NonNull
    private final Supplier<Set<String>> watchedPackages;
    @Nullable
    private volatile DoomScrollListener listener;
    @Nullable
    private ScheduledExecutorService executor;
    @Nullable
    private ScheduledFuture<?> pollingTask;
    private long lastQueryMillis;
    private boolean monitoring;

    /**
     * @param watchedPackages the package IDs to watch, read on the polling thread at every poll
     *                        (e.g. {@code WatchedAppsRepository::getCurrent}).
     */
    public UsageStatsDoomScrollMonitor(@NonNull Context context,
                                       @NonNull Supplier<Set<String>> watchedPackages) {
        appContext = context.getApplicationContext();
        this.watchedPackages = watchedPackages;
        usageStatsManager = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        displayManager = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        sessionTracker = new DoomScrollSessionTracker(watchedPackages.get(), PENALTY_INTERVAL_MILLIS,
                WARNING_INTERVAL_MILLIS, AWAY_GRACE_MILLIS, MAX_POLL_GAP_MILLIS);
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

    @MainThread
    @Override
    public void startFromForeground() {
        if (!isPermissionGranted()) {
            Log.i(TAG, "Usage Access not granted; monitoring service was not started");
            return;
        }
        try {
            ContextCompat.startForegroundService(
                    appContext, new Intent(appContext, DoomScrollMonitorService.class));
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Monitoring service could not start; polling without it", exception);
            startMonitoring();
        }
    }

    @AnyThread
    @Override
    public void stopMonitoring() {
        appContext.stopService(new Intent(appContext, DoomScrollMonitorService.class));
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
            sessionTracker.reset();
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
        Set<String> watched = watchedPackages.get();
        UsageEvents events = usageStatsManager.queryEvents(begin, now);
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            lastQueryMillis = now;
            sessionTracker.setMonitoredPackages(watched, begin);
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            handleUsageEvent(event);
        }
        reportDueTime(now);
    }

    private void handleUsageEvent(@NonNull UsageEvents.Event event) {
        int type = event.getEventType();
        String packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            if (type == EVENT_FOREGROUND) {
                sessionTracker.onForeground(packageName, event.getTimeStamp());
            } else if (type == EVENT_BACKGROUND) {
                sessionTracker.onBackground(packageName, event.getTimeStamp());
            }
        }
    }

    private void reportDueTime(long nowMillis) {
        boolean countable = isScreenOnInPortrait();
        DoomScrollSessionTracker.Tick tick;
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            tick = sessionTracker.onPoll(nowMillis, countable);
        }
        if (tick != null && tick.penaltyIntervals > 0) {
            notifyListener(new DoomScrollEvent(tick.sourcePackage,
                    tick.penaltyIntervals * PENALTY_INTERVAL_MILLIS,
                    tick.penaltyIntervals * HP_PER_PENALTY_INTERVAL,
                    nowMillis));
        }
        if (tick != null && tick.warningSessionMillis > 0L) {
            DoomScrollListener currentListener = listener;
            if (currentListener != null) {
                currentListener.onDoomScrollWarning(tick.sourcePackage, tick.warningSessionMillis);
            }
        }
    }

    /** The display's current size reflects the rotation the foreground app is using. */
    @SuppressWarnings("deprecation")
    private boolean isScreenOnInPortrait() {
        if (powerManager == null || !powerManager.isInteractive() || displayManager == null) {
            return false;
        }
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return false;
        }
        Point size = new Point();
        display.getRealSize(size);
        return DoomScrollSessionTracker.isPortrait(size.x, size.y);
    }

    private void notifyListener(@NonNull DoomScrollEvent event) {
        DoomScrollListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onDoomScrollDetected(event);
        }
    }
}
