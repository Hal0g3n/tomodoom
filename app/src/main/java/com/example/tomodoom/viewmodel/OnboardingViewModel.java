package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.repository.PetStateRepository;

/** ViewModel for the first-launch onboarding screen, where the user names their pet. */
public class OnboardingViewModel extends ViewModel {

    @NonNull
    private final PetStateRepository petStateRepository;

    public OnboardingViewModel(@NonNull PetStateRepository petStateRepository) {
        this.petStateRepository = petStateRepository;
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
     * Saves the name and completes onboarding.
     *
     * @return {@code false} (and saves nothing) if the name is invalid.
     */
    public boolean submitName(@Nullable String name) {
        String normalized = PetNames.normalize(name);
        if (normalized == null) {
            return false;
        }
        petStateRepository.completeOnboarding(normalized);
        return true;
    }
}
