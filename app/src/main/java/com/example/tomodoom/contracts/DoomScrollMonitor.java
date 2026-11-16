package com.example.tomodoom.contracts;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

/**
 * Contract for the <b>Doom Scroll Agent</b>: detects doom-scrolling sessions (e.g. via
 * {@code UsageStatsManager} polling or an {@code AccessibilityService}) and reports them to a
 * {@link DoomScrollListener}.
 *
 * <h3>Wiring</h3>
 * {@code AppContainer} calls {@link #setListener(DoomScrollListener)} with
 * {@code repository.DoomScrollHpBridge}, which turns each event into
 * {@code PetStateRepository.decreaseHp(...)}. Implementations must <b>never</b> touch
 * {@code PetStateRepository} directly. They only report events.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *     <li>{@code TomodoomApplication#onCreate()} calls {@link #startMonitoring()} when
 *     {@link #isPermissionGranted()} is true. A permission UI flow (owned by the Doom Scroll
 *     Agent) should call it again after the user grants access.</li>
 *     <li>{@link #startMonitoring()} and {@link #stopMonitoring()} are idempotent.</li>
 *     <li>Implementations run in the default app process (no {@code android:process}).</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * Methods may be called from any thread. {@link DoomScrollListener} callbacks may be delivered
 * on any thread; the bridge is thread-safe.
 *
 * <p>Default implementation: {@code stubs.StubDoomScrollMonitor}.</p>
 */
public interface DoomScrollMonitor {

    /**
     * Begins monitoring. If {@link #isPermissionGranted()} is false, this logs and does nothing.
     * Idempotent.
     */
    @AnyThread
    void startMonitoring();

    /** Stops monitoring and releases resources (receivers, polling, services). Idempotent. */
    @AnyThread
    void stopMonitoring();

    /** @return true while monitoring is active. */
    @AnyThread
    boolean isMonitoring();

    /**
     * @return true if every OS permission/setting the implementation needs (usage access,
     * accessibility service enabled, etc.) is currently granted. Must be cheap; may be called
     * from {@code onResume}.
     */
    @AnyThread
    boolean isPermissionGranted();

    /**
     * Registers the single listener, replacing any previous one; {@code null} detaches.
     * Events detected while no listener is set are dropped.
     */
    @AnyThread
    void setListener(@Nullable DoomScrollListener listener);
}
