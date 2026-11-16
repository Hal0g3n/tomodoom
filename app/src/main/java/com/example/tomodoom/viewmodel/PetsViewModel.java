package com.example.tomodoom.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.model.AccessoryCatalog;
import com.example.tomodoom.model.AccessoryItem;
import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.DoomScrollEvent;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.PetStateRepository;

import java.util.List;

/**
 * ViewModel for the Pets page: exposes the pet state from {@link PetStateRepository} plus
 * page-local UI state (the selected customisation slot), and forwards user intents.
 */
public class PetsViewModel extends ViewModel {

    /** Simulated session length used by the debug doom-scroll button. */
    private static final long DEBUG_SESSION_MILLIS = 15L * 60L * 1000L;

    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final DoomScrollListener doomScrollListener;
    /** UI-only state; written on the main thread only, so setValue is fine here. */
    private final MutableLiveData<String> selectedSlot = new MutableLiveData<>(AccessorySlot.ALL.get(0));

    public PetsViewModel(@NonNull PetStateRepository petStateRepository,
                         @NonNull DoomScrollListener doomScrollListener) {
        this.petStateRepository = petStateRepository;
        this.doomScrollListener = doomScrollListener;
    }

    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }

    /** The {@link AccessorySlot} currently shown in the customisation controls. */
    @NonNull
    public LiveData<String> getSelectedSlot() {
        return selectedSlot;
    }

    @MainThread
    public void selectSlot(@NonNull String slot) {
        if (AccessorySlot.isValid(slot) && !slot.equals(selectedSlot.getValue())) {
            selectedSlot.setValue(slot);
        }
    }

    @NonNull
    public List<AccessoryItem> getItemsForSlot(@NonNull String slot) {
        return AccessoryCatalog.getItemsForSlot(slot);
    }

    /** Equips {@code itemId} into {@code slot}; {@code null} unequips. Persists and re-renders via LiveData. */
    public void equipItem(@NonNull String slot, @Nullable String itemId) {
        petStateRepository.equipItem(slot, itemId);
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
