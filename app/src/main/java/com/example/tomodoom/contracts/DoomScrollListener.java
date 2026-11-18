package com.example.tomodoom.contracts;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.model.DoomScrollEvent;

/**
 * Receives doom-scroll detections from a {@link DoomScrollMonitor}.
 *
 * <p>Production implementation: {@code repository.DoomScrollHpBridge}, which applies
 * {@link DoomScrollEvent#getHpPenalty()} to the pet.</p>
 */
public interface DoomScrollListener {

    /**
     * Called for each penalty a monitor charges. May be invoked on <b>any thread</b>;
     * implementations must be thread-safe and must not block.
     *
     * @param event the penalty to apply; never null.
     */
    @AnyThread
    void onDoomScrollDetected(@NonNull DoomScrollEvent event);

    /**
     * Called each time a session reaches another warning milestone (every 10 minutes of counted
     * time). May be invoked on <b>any thread</b>; must not block. Optional.
     *
     * @param sourcePackage the app in use when the milestone was reached.
     * @param sessionMillis the counted session length at this milestone.
     */
    @AnyThread
    default void onDoomScrollWarning(@NonNull String sourcePackage, long sessionMillis) {
    }
}
