package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.ChatTextScale;
import com.example.tomodoom.model.InstalledApp;
import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.model.PetSpecies;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.model.WatchedApps;
import com.example.tomodoom.repository.PetStateRepository;
import com.example.tomodoom.repository.SettingsRepository;
import com.example.tomodoom.repository.WatchedAppsRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * ViewModel for the Settings page: the pet's name and species, the watched apps (edited in
 * {@code WatchedAppsActivity}), the chat text size and the background-music volume. Every change goes through its repository.
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
    @NonNull
    private final Supplier<List<InstalledApp>> installedApps;
    /** Installed launcher apps; {@code null} until the first load finishes. */
    @NonNull
    private final MutableLiveData<List<InstalledApp>> installedAppsLiveData = new MutableLiveData<>();
    @NonNull
    private final MediatorLiveData<Integer> watchedCount = new MediatorLiveData<>();
    @NonNull
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "settings-apps-loader");
        thread.setDaemon(true);
        return thread;
    });

    public SettingsViewModel(@NonNull PetStateRepository petStateRepository,
                             @NonNull WatchedAppsRepository watchedAppsRepository,
                             @NonNull SettingsRepository settingsRepository,
                             @NonNull BooleanSupplier usageAccessGranted,
                             @NonNull Supplier<List<InstalledApp>> installedApps) {
        this.petStateRepository = petStateRepository;
        this.watchedAppsRepository = watchedAppsRepository;
        this.settingsRepository = settingsRepository;
        this.usageAccessGranted = usageAccessGranted;
        this.installedApps = installedApps;
        watchedCount.addSource(watchedAppsRepository.getWatchedPackages(),
                watched -> updateWatchedCount());
        watchedCount.addSource(installedAppsLiveData, apps -> updateWatchedCount());
        refreshInstalledApps();
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

    /**
     * How many watched apps are installed launcher apps, the same count the watched-apps picker
     * shows. The saved set can name apps that aren't installed (the defaults list eight social
     * apps), and those don't count. Emits nothing until the installed apps have loaded.
     */
    @NonNull
    public LiveData<Integer> getWatchedCount() {
        return watchedCount;
    }

    /** Reloads the installed apps off the main thread (apps may come and go while away). */
    public void refreshInstalledApps() {
        executor.execute(() -> {
            List<InstalledApp> loaded;
            try {
                loaded = installedApps.get();
            } catch (RuntimeException exception) {
                loaded = Collections.emptyList();
            }
            installedAppsLiveData.postValue(Collections.unmodifiableList(loaded));
        });
    }

    private void updateWatchedCount() {
        List<InstalledApp> apps = installedAppsLiveData.getValue();
        Set<String> watched = watchedAppsRepository.getWatchedPackages().getValue();
        if (apps == null || watched == null) {
            return;
        }
        int count = WatchedApps.countWatched(apps, watched);
        Integer previous = watchedCount.getValue();
        if (previous == null || previous != count) {
            watchedCount.setValue(count);
        }
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

    // ---- Background music ----

    /** The background-music volume in percent (0 = silent). */
    @NonNull
    public LiveData<Integer> getMusicVolume() {
        return settingsRepository.getMusicVolume();
    }

    /** Sets the background-music volume in percent; it snaps to a {@code MusicVolume} step. */
    public void setMusicVolume(int percent) {
        settingsRepository.setMusicVolume(percent);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
