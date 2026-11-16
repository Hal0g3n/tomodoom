package com.example.tomodoom.repository;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.model.DoomScrollEvent;

/**
 * Bridges {@code DoomScrollMonitor} detections to the pet: every {@link DoomScrollEvent}
 * becomes {@link PetStateRepository#decreaseHp(int, String)} with reason
 * {@code "doom_scroll:<package>"}.
 *
 * <p>Registered as the monitor's listener in {@code AppContainer}. Thread-safe, because the
 * repository is.</p>
 */
public final class DoomScrollHpBridge implements DoomScrollListener {

    private static final String TAG = "DoomScrollHpBridge";

    @NonNull
    private final PetStateRepository petStateRepository;

    public DoomScrollHpBridge(@NonNull PetStateRepository petStateRepository) {
        this.petStateRepository = petStateRepository;
    }

    @AnyThread
    @Override
    public void onDoomScrollDetected(@NonNull DoomScrollEvent event) {
        if (event.getHpPenalty() <= 0) {
            Log.d(TAG, "ignoring event with no penalty: " + event);
            return;
        }
        String pkg = event.getSourcePackage() != null ? event.getSourcePackage() : "unknown";
        petStateRepository.decreaseHp(event.getHpPenalty(), "doom_scroll:" + pkg);
    }
}
