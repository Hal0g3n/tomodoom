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
import java.util.function.BooleanSupplier;

/**
 * ViewModel for the first-launch onboarding screen: the user picks and names their pet, then is
 * asked for Usage Access, which apps to track and then notifications
 * ({@link OnboardingPermissionFlow}).
 */
public class OnboardingViewModel extends ViewModel {

    @NonNull
    private final PetStateRepository petStateRepository;
    /** UI state: the species card currently selected (main thread only). */
    @NonNull
    private final MutableLiveData<String> selectedSpecies;
    /** Whether Usage Access is granted (the doom-scroll monitor's check). */
    @NonNull
    private final BooleanSupplier usageAccessGranted;
    /** UI state (main thread only): the permission steps the user skipped or answered. */
    private boolean usageSkipped;
    private boolean appsHandled;
    private boolean notificationsHandled;

    public OnboardingViewModel(@NonNull PetStateRepository petStateRepository,
                               @NonNull BooleanSupplier usageAccessGranted) {
        this.petStateRepository = petStateRepository;
        this.usageAccessGranted = usageAccessGranted;
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

    /** @return the pet's name, for the permission step's title. */
    @NonNull
    public String getPetName() {
        return petStateRepository.getCurrentState().getPetName();
    }

    public boolean isUsageAccessGranted() {
        return usageAccessGranted.getAsBoolean();
    }

    public boolean isUsageSkipped() {
        return usageSkipped;
    }

    public boolean isNotificationsHandled() {
        return notificationsHandled;
    }

    public boolean isAppsHandled() {
        return appsHandled;
    }

    /** The user tapped "Not now" on the Usage Access step. Main thread only. */
    public void skipUsageAccess() {
        usageSkipped = true;
    }

    /** The user came back from the watched-apps picker or skipped it. Main thread only. */
    public void onAppsHandled() {
        appsHandled = true;
    }

    /** The user answered or skipped the notification prompt. Main thread only. */
    public void onNotificationsHandled() {
        notificationsHandled = true;
    }

    /**
     * @param notificationsNeeded whether notifications still need a runtime grant
     * @return the permission step to show now
     */
    @NonNull
    public OnboardingPermissionFlow.Step currentPermissionStep(boolean notificationsNeeded) {
        return OnboardingPermissionFlow.current(isUsageAccessGranted(), usageSkipped,
                appsHandled, notificationsNeeded, notificationsHandled);
    }
}
