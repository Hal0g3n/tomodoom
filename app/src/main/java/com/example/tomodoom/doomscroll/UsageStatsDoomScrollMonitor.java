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
import com.example.tomodoom.model.DoomScrollMonitorStatus;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Detects doom-scrolling in the apps the user watches using Android Usage Access. Polls every
 * 15 seconds and charges the configured HP (default 1, set in the debug panel) per minute a
 * watched app is on screen in portrait, and warns at 10, 20, 40 … HP lost in a session. The watched set
 * is read at every poll, so a change in the picker applies within one poll. Landscape (such
 * as fullscreen video) doesn't count. {@link DoomScrollSessionTracker} holds the session rules.
 *
 * <p>An unexpected exception in one poll is logged and kept for {@link #getStatus()}, and the next
 * poll still runs: an exception escaping a {@code scheduleWithFixedDelay} task would silently
 * cancel every later poll while {@link #isMonitoring()} kept saying true.</p>
 */
public final class UsageStatsDoomScrollMonitor implements DoomScrollMonitor {

    private static final String TAG = "UsageStatsDoomScrollMonitor";
    private static final long POLL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long MAX_POLL_GAP_MILLIS = TimeUnit.SECONDS.toMillis(45);
    private static final long PENALTY_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
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
    @NonNull
    private final IntSupplier hpPerMinute;
    @Nullable
    private volatile DoomScrollListener listener;
    @Nullable
    private ScheduledExecutorService executor;
    @Nullable
    private ScheduledFuture<?> pollingTask;
    private long lastQueryMillis;
    private boolean monitoring;
    // Debug status (getStatus), all guarded by lock.
    private long lastPollMillis = DoomScrollMonitorStatus.NEVER;
    private long pollCount;
    private long lastGapMillis = DoomScrollMonitorStatus.NEVER;
    private int lastEventCount;
    private boolean lastCountable;
    private int lastDisplayWidth;
    private int lastDisplayHeight;
    private long hpChargedSinceStart;
    @Nullable
    private String lastError;
    private long lastErrorMillis = DoomScrollMonitorStatus.NEVER;

    /**
     * @param watchedPackages the package IDs to watch, read on the polling thread at every poll
     *                        (e.g. {@code WatchedAppsRepository::getCurrent}).
     * @param hpPerMinute     HP charged per counted minute, read at each penalty on the polling
     *                        thread (e.g. {@code SettingsRepository::getCurrentDoomScrollHpPerMinute}).
     */
    public UsageStatsDoomScrollMonitor(@NonNull Context context,
                                       @NonNull Supplier<Set<String>> watchedPackages,
                                       @NonNull IntSupplier hpPerMinute) {
        appContext = context.getApplicationContext();
        this.watchedPackages = watchedPackages;
        this.hpPerMinute = hpPerMinute;
        usageStatsManager = (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        displayManager = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        sessionTracker = new DoomScrollSessionTracker(watchedPackages.get(), PENALTY_INTERVAL_MILLIS,
                AWAY_GRACE_MILLIS, MAX_POLL_GAP_MILLIS);
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
            lastPollMillis = DoomScrollMonitorStatus.NEVER;
            pollCount = 0L;
            lastGapMillis = DoomScrollMonitorStatus.NEVER;
            lastEventCount = 0;
            hpChargedSinceStart = 0L;
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

    @AnyThread
    @Nullable
    @Override
    public DoomScrollMonitorStatus getStatus() {
        synchronized (lock) {
            if (!monitoring && pollCount == 0L && lastError == null) {
                return null;
            }
            return new DoomScrollMonitorStatus(lastPollMillis, pollCount, lastGapMillis,
                    lastEventCount, sessionTracker.getForegroundPackage(),
                    sessionTracker.getLastSeenPackage(), lastCountable, lastDisplayWidth,
                    lastDisplayHeight, sessionTracker.getCountedMillis(),
                    sessionTracker.getChargedIntervals(), sessionTracker.getSessionHpLost(),
                    sessionTracker.getNextWarningHp(), hpChargedSinceStart,
                    lastError, lastErrorMillis);
        }
    }

    private void pollSafely() {
        try {
            pollUsageEvents();
        } catch (SecurityException exception) {
            Log.w(TAG, "Usage Access was revoked; stopping doom-scroll monitoring", exception);
            stopMonitoring();
        } catch (RuntimeException exception) {
            // Never let it escape: that would cancel every later poll without a trace.
            Log.w(TAG, "Doom-scroll poll failed; trying again at the next poll", exception);
            synchronized (lock) {
                lastError = exception.getClass().getSimpleName()
                        + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
                lastErrorMillis = System.currentTimeMillis();
            }
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
        int eventCount = 0;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (handleUsageEvent(event)) {
                eventCount++;
            }
        }
        reportDueTime(now, eventCount);
    }

    /** @return true if the event was a foreground/background change the tracker saw. */
    private boolean handleUsageEvent(@NonNull UsageEvents.Event event) {
        int type = event.getEventType();
        String packageName = event.getPackageName();
        if (packageName == null) {
            return false;
        }
        synchronized (lock) {
            if (!monitoring) {
                return false;
            }
            if (type == EVENT_FOREGROUND) {
                sessionTracker.onForeground(packageName, event.getTimeStamp());
                return true;
            } else if (type == EVENT_BACKGROUND) {
                sessionTracker.onBackground(packageName, event.getTimeStamp());
                return true;
            }
            return false;
        }
    }

    private void reportDueTime(long nowMillis, int eventCount) {
        Point size = readDisplaySize();
        boolean countable = size != null && powerManager != null && powerManager.isInteractive()
                && DoomScrollSessionTracker.isPortrait(size.x, size.y);
        DoomScrollSessionTracker.Tick tick;
        int hpPenalty = 0;
        synchronized (lock) {
            if (!monitoring) {
                return;
            }
            tick = sessionTracker.onPoll(nowMillis, countable, hpPerMinute.getAsInt());
            if (tick != null) {
                hpPenalty = tick.hpPenalty;
                hpChargedSinceStart += hpPenalty;
            }
            lastGapMillis = lastPollMillis == DoomScrollMonitorStatus.NEVER
                    ? DoomScrollMonitorStatus.NEVER : nowMillis - lastPollMillis;
            lastPollMillis = nowMillis;
            pollCount++;
            lastEventCount = eventCount;
            lastCountable = countable;
            lastDisplayWidth = size == null ? 0 : size.x;
            lastDisplayHeight = size == null ? 0 : size.y;
            Log.d(TAG, "poll #" + pollCount + " gap=" + lastGapMillis + "ms events=" + eventCount
                    + " fg=" + sessionTracker.getForegroundPackage()
                    + " lastSeen=" + sessionTracker.getLastSeenPackage()
                    + " display=" + lastDisplayWidth + "x" + lastDisplayHeight
                    + " countable=" + countable
                    + " counted=" + sessionTracker.getCountedMillis() + "ms"
                    + " sessionHp=" + sessionTracker.getSessionHpLost()
                    + " hp=" + hpPenalty);
        }
        if (tick != null && tick.penaltyIntervals > 0) {
            notifyListener(new DoomScrollEvent(tick.sourcePackage,
                    tick.penaltyIntervals * PENALTY_INTERVAL_MILLIS,
                    hpPenalty,
                    nowMillis));
        }
        if (tick != null && tick.warningHpLost > 0) {
            DoomScrollListener currentListener = listener;
            if (currentListener != null) {
                currentListener.onDoomScrollWarning(tick.sourcePackage,
                        tick.sessionCountedMillis, tick.warningHpLost);
            }
        }
    }

    /**
     * The display's current size, which reflects the rotation the foreground app is using, or
     * {@code null} if it can't be read.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    private Point readDisplaySize() {
        if (displayManager == null) {
            return null;
        }
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return null;
        }
        Point size = new Point();
        display.getRealSize(size);
        return size;
    }

    private void notifyListener(@NonNull DoomScrollEvent event) {
        DoomScrollListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onDoomScrollDetected(event);
        }
    }
}
