package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.model.DoomScrollEvent;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.PetStateRepository;

/**
 * ViewModel for the Pets page: exposes the pet state from {@link PetStateRepository} and forwards
 * the debug actions. Customisation lives in {@link CustomiseViewModel}.
 */
public class PetsViewModel extends ViewModel {

    /** Simulated session length used by the debug doom-scroll button. */
    private static final long DEBUG_SESSION_MILLIS = 15L * 60L * 1000L;

    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final DoomScrollListener doomScrollListener;

    public PetsViewModel(@NonNull PetStateRepository petStateRepository,
                         @NonNull DoomScrollListener doomScrollListener) {
        this.petStateRepository = petStateRepository;
        this.doomScrollListener = doomScrollListener;
    }

    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }

    /** Debug: injects a synthetic doom-scroll event through the real penalty path. */
    public void debugSimulateDoomScroll() {
        doomScrollListener.onDoomScrollDetected(DoomScrollEvent.now(
                "debug.simulated", DEBUG_SESSION_MILLIS, DoomScrollEvent.DEFAULT_HP_PENALTY));
    }

    /** Debug: restores the default pet. */
    public void debugResetPet() {
        petStateRepository.resetPetState();
    }
}
