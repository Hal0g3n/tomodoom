package com.example.tomodoom.repository;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.model.DoomScrollEvent;

/**
 * Records each doom-scroll detection in {@link StatsRepository}, then passes it on unchanged
 * (to {@link DoomScrollHpBridge} in {@code AppContainer}). Thread-safe.
 */
public final class StatsRecordingDoomScrollListener implements DoomScrollListener {

    @NonNull
    private final StatsRepository statsRepository;
    @NonNull
    private final DoomScrollListener delegate;

    public StatsRecordingDoomScrollListener(@NonNull StatsRepository statsRepository,
                                            @NonNull DoomScrollListener delegate) {
        this.statsRepository = statsRepository;
        this.delegate = delegate;
    }

    @AnyThread
    @Override
    public void onDoomScrollDetected(@NonNull DoomScrollEvent event) {
        if (event.getDurationMillis() > 0) {
            statsRepository.recordDoomScroll(event);
        }
        delegate.onDoomScrollDetected(event);
    }

    @AnyThread
    @Override
    public void onDoomScrollWarning(@NonNull String sourcePackage, long sessionMillis,
                                    int sessionHpLost) {
        delegate.onDoomScrollWarning(sourcePackage, sessionMillis, sessionHpLost);
    }
}
