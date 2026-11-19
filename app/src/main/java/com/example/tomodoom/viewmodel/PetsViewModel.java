package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.PetStateRepository;

/**
 * ViewModel for the Pets page: exposes the pet state from {@link PetStateRepository}.
 * Customisation lives in {@link CustomiseViewModel}.
 */
public class PetsViewModel extends ViewModel {

    @NonNull
    private final PetStateRepository petStateRepository;

    public PetsViewModel(@NonNull PetStateRepository petStateRepository) {
        this.petStateRepository = petStateRepository;
    }

    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }
}
