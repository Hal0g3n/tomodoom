package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.InstalledApp;
import com.example.tomodoom.model.WatchedApps;
import com.example.tomodoom.repository.WatchedAppsRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * ViewModel for the watched-apps picker. Loads the installed apps once, off the main thread, and
 * sorts them once (watched first) so rows don't jump while the user toggles them.
 */
public class WatchedAppsViewModel extends ViewModel {

    @NonNull
    private final WatchedAppsRepository repository;
    @NonNull
    private final MutableLiveData<List<InstalledApp>> apps = new MutableLiveData<>();
    @NonNull
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "watched-apps-loader");
        thread.setDaemon(true);
        return thread;
    });

    public WatchedAppsViewModel(@NonNull WatchedAppsRepository repository,
                                @NonNull Supplier<List<InstalledApp>> installedApps) {
        this.repository = repository;
        executor.execute(() -> {
            List<InstalledApp> loaded;
            try {
                loaded = installedApps.get();
            } catch (RuntimeException exception) {
                loaded = Collections.emptyList();
            }
            apps.postValue(Collections.unmodifiableList(
                    WatchedApps.sortForDisplay(loaded, repository.getCurrent())));
        });
    }

    /** The installed apps in display order; {@code null} while loading. */
    @NonNull
    public LiveData<List<InstalledApp>> getApps() {
        return apps;
    }

    @NonNull
    public LiveData<Set<String>> getWatchedPackages() {
        return repository.getWatchedPackages();
    }

    public void setWatched(@NonNull String packageName, boolean watched) {
        repository.setWatched(packageName, watched);
    }

    /** @return the apps list once loaded, else {@code null}. */
    @Nullable
    public List<InstalledApp> getLoadedApps() {
        return apps.getValue();
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
