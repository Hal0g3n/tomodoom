package com.example.tomodoom.stubs;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.model.DoomScrollEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * No-op {@link DoomScrollMonitor}: it detects nothing, reports permission as granted, and logs
 * lifecycle calls. {@link #simulateDoomScroll} fires a synthetic event for debugging and tests.
 *
 * <p>To be replaced by the Doom Scroll Agent in {@code AppContainer}.</p>
 */
public final class StubDoomScrollMonitor implements DoomScrollMonitor {

    private static final String TAG = "StubDoomScrollMonitor";

    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    @Nullable
    private volatile DoomScrollListener listener;

    @AnyThread
    @Override
    public void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            Log.i(TAG, "monitoring started (stub: no real detection)");
        }
    }

    @AnyThread
    @Override
    public void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            Log.i(TAG, "monitoring stopped");
        }
    }

    @AnyThread
    @Override
    public boolean isMonitoring() {
        return monitoring.get();
    }

    @AnyThread
    @Override
    public boolean isPermissionGranted() {
        return true;
    }

    @AnyThread
    @Override
    public void setListener(@Nullable DoomScrollListener listener) {
        this.listener = listener;
    }

    /**
     * <b>Debug trigger:</b> delivers a synthetic {@link DoomScrollEvent} to the listener on the
     * calling thread, as if a session had been detected. Ignored while not monitoring.
     *
     * @return true if the event was delivered.
     */
    @AnyThread
    public boolean simulateDoomScroll(@Nullable String sourcePackage, long durationMillis, int hpPenalty) {
        DoomScrollListener l = listener;
        if (!monitoring.get() || l == null) {
            Log.w(TAG, "simulateDoomScroll ignored (monitoring=" + monitoring.get()
                    + ", listener=" + (l != null) + ")");
            return false;
        }
        l.onDoomScrollDetected(DoomScrollEvent.now(sourcePackage, durationMillis, hpPenalty));
        return true;
    }
}
