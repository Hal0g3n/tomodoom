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
     * Called each time a session reaches another warning milestone: when it has cost 10 HP,
     * then each time its HP loss doubles (20, 40, 80 …). May be invoked on <b>any thread</b>;
     * must not block. Optional.
     *
     * @param sourcePackage the app in use when the milestone was reached.
     * @param sessionMillis the counted session length at this milestone.
     * @param sessionHpLost the HP-lost threshold this milestone marks (10, 20, 40 …).
     */
    @AnyThread
    default void onDoomScrollWarning(@NonNull String sourcePackage, long sessionMillis,
                                     int sessionHpLost) {
    }
}
