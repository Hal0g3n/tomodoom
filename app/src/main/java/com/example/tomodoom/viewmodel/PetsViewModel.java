package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.InactivityDecayTracker;
import com.example.tomodoom.repository.PetStateRepository;

/**
 * ViewModel for the Pets page: exposes the pet state from {@link PetStateRepository} and the
 * "missed you" notice after inactivity decay ({@link InactivityDecayTracker}).
 * Customisation lives in {@link CustomiseViewModel}.
 */
public class PetsViewModel extends ViewModel {

    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final InactivityDecayTracker inactivityDecayTracker;

    public PetsViewModel(@NonNull PetStateRepository petStateRepository,
                         @NonNull InactivityDecayTracker inactivityDecayTracker) {
        this.petStateRepository = petStateRepository;
        this.inactivityDecayTracker = inactivityDecayTracker;
    }

    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }

    /** @return the latest pet state, synchronously. */
    @NonNull
    public PetState getCurrentState() {
        return petStateRepository.getCurrentState();
    }

    /** HP lost while the user was away and not yet shown; 0 when there is nothing to show. */
    @NonNull
    public LiveData<Integer> getDecayNotice() {
        return inactivityDecayTracker.getPendingNotice();
    }

    /** The decay notice was shown. */
    public void acknowledgeDecayNotice() {
        inactivityDecayTracker.acknowledgeNotice();
    }
}
