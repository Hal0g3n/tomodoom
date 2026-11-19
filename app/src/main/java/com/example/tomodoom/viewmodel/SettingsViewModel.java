package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.ChatTextScale;
import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.model.PetSpecies;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.PetStateRepository;
import com.example.tomodoom.repository.SettingsRepository;
import com.example.tomodoom.repository.WatchedAppsRepository;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * ViewModel for the Settings page: the pet's name and species, the watched apps (edited in
 * {@code WatchedAppsActivity}) and the chat text size. Every change goes through its repository.
 */
public class SettingsViewModel extends ViewModel {

    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final WatchedAppsRepository watchedAppsRepository;
    @NonNull
    private final SettingsRepository settingsRepository;
    @NonNull
    private final BooleanSupplier usageAccessGranted;

    public SettingsViewModel(@NonNull PetStateRepository petStateRepository,
                             @NonNull WatchedAppsRepository watchedAppsRepository,
                             @NonNull SettingsRepository settingsRepository,
                             @NonNull BooleanSupplier usageAccessGranted) {
        this.petStateRepository = petStateRepository;
        this.watchedAppsRepository = watchedAppsRepository;
        this.settingsRepository = settingsRepository;
        this.usageAccessGranted = usageAccessGranted;
    }

    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }

    @NonNull
    public PetState getCurrentState() {
        return petStateRepository.getCurrentState();
    }

    // ---- Name ----

    /**
     * @return whether saving {@code input} would rename the pet: it is a valid name and differs
     * from {@code currentName} once normalised.
     */
    public static boolean isNameChange(@Nullable String input, @NonNull String currentName) {
        String normalized = PetNames.normalize(input);
        return normalized != null && !normalized.equals(currentName);
    }

    /** @return whether {@code input} is a valid name that differs from the pet's current one. */
    public boolean canSaveName(@Nullable String input) {
        return isNameChange(input, getCurrentState().getPetName());
    }

    /**
     * Renames the pet.
     *
     * @return the saved (normalised) name, or {@code null} if {@code input} isn't a valid name.
     */
    @Nullable
    public String saveName(@Nullable String input) {
        String normalized = PetNames.normalize(input);
        if (normalized == null) {
            return null;
        }
        petStateRepository.renamePet(normalized);
        return normalized;
    }

    // ---- Species ----

    /** @return every pet the user can choose, in display order. */
    @NonNull
    public List<String> getSpecies() {
        return PetSpecies.ALL;
    }

    /**
     * Switches the pet to {@code species}.
     *
     * @return whether the species changed (unknown IDs and the current one change nothing).
     */
    public boolean selectSpecies(@Nullable String species) {
        if (!PetSpecies.isValid(species)
                || species.equals(getCurrentState().getPetSpecies())) {
            return false;
        }
        petStateRepository.changeSpecies(species);
        return true;
    }

    // ---- Watched apps ----

    @NonNull
    public LiveData<Set<String>> getWatchedPackages() {
        return watchedAppsRepository.getWatchedPackages();
    }

    public boolean isUsageAccessGranted() {
        return usageAccessGranted.getAsBoolean();
    }

    // ---- Chat text size ----

    @NonNull
    public LiveData<Float> getChatTextScale() {
        return settingsRepository.getChatTextScale();
    }

    /** Sets the chat text size to the {@link ChatTextScale} step at {@code index}. */
    public void setChatTextScaleIndex(int index) {
        settingsRepository.setChatTextScale(ChatTextScale.stepAt(index));
    }
}
