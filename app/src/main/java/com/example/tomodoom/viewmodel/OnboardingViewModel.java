package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.model.PetSpecies;
import com.example.tomodoom.repository.PetStateRepository;

import java.util.List;

/** ViewModel for the first-launch onboarding screen, where the user picks and names their pet. */
public class OnboardingViewModel extends ViewModel {

    @NonNull
    private final PetStateRepository petStateRepository;
    /** UI state: the species card currently selected (main thread only). */
    @NonNull
    private final MutableLiveData<String> selectedSpecies;

    public OnboardingViewModel(@NonNull PetStateRepository petStateRepository) {
        this.petStateRepository = petStateRepository;
        this.selectedSpecies = new MutableLiveData<>(
                petStateRepository.getCurrentState().getPetSpecies());
    }

    /** @return every pet the user can choose, in display order. */
    @NonNull
    public List<String> getSpecies() {
        return PetSpecies.ALL;
    }

    @NonNull
    public LiveData<String> getSelectedSpecies() {
        return selectedSpecies;
    }

    /** Selects {@code species}; unknown IDs are ignored. Main thread only. */
    public void selectSpecies(@Nullable String species) {
        if (PetSpecies.isValid(species)) {
            selectedSpecies.setValue(species);
        }
    }

    public boolean isOnboardingComplete() {
        return petStateRepository.isOnboardingComplete();
    }

    /** @return the name to prefill: the pet's current (or default) name. */
    @NonNull
    public String getSuggestedName() {
        return PetNames.orDefault(petStateRepository.getCurrentState().getPetName());
    }

    /** @return whether {@code name} would be accepted by {@link #submitName(String)}. */
    public boolean isValidName(@Nullable String name) {
        return PetNames.isValid(name);
    }

    /**
     * Saves the name and the selected species and completes onboarding.
     *
     * @return {@code false} (and saves nothing) if the name is invalid.
     */
    public boolean submitName(@Nullable String name) {
        String normalized = PetNames.normalize(name);
        if (normalized == null) {
            return false;
        }
        petStateRepository.completeOnboarding(normalized, selectedSpecies.getValue());
        return true;
    }
}
