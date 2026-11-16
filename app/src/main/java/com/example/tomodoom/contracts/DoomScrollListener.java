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
     * Called once per detected doom-scroll session. May be invoked on <b>any thread</b>;
     * implementations must be thread-safe and must not block.
     *
     * @param event the detected session; never null.
     */
    @AnyThread
    void onDoomScrollDetected(@NonNull DoomScrollEvent event);
}
