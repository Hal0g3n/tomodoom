package com.example.tomodoom.di;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.viewmodel.ChatViewModel;
import com.example.tomodoom.viewmodel.CustomiseViewModel;
import com.example.tomodoom.viewmodel.DebugViewModel;
import com.example.tomodoom.viewmodel.OnboardingViewModel;
import com.example.tomodoom.viewmodel.PetsViewModel;
import com.example.tomodoom.viewmodel.SettingsViewModel;
import com.example.tomodoom.viewmodel.StatsViewModel;
import com.example.tomodoom.viewmodel.WatchedAppsViewModel;

/**
 * Builds ViewModels with constructor-injected dependencies from {@link AppContainer}.
 *
 * <p>Usage: {@code new ViewModelProvider(this, ViewModelFactory.from(requireContext())).get(PetsViewModel.class)}.
 * Register new ViewModels here.</p>
 */
public final class ViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    private final AppContainer container;

    public ViewModelFactory(@NonNull AppContainer container) {
        this.container = container;
    }

    @NonNull
    public static ViewModelFactory from(@NonNull Context context) {
        return new ViewModelFactory(AppContainer.from(context));
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PetsViewModel.class)) {
            return (T) new PetsViewModel(container.getPetStateRepository(),
                    container.getInactivityDecayTracker());
        }
        if (modelClass.isAssignableFrom(CustomiseViewModel.class)) {
            return (T) new CustomiseViewModel(container.getPetStateRepository());
        }
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            return (T) new ChatViewModel(container.getChatRepository(),
                    container.getPetStateRepository(), container.getSettingsRepository());
        }
        if (modelClass.isAssignableFrom(OnboardingViewModel.class)) {
            return (T) new OnboardingViewModel(container.getPetStateRepository(),
                    container.getDoomScrollMonitor()::isPermissionGranted);
        }
        if (modelClass.isAssignableFrom(WatchedAppsViewModel.class)) {
            return (T) new WatchedAppsViewModel(container.getWatchedAppsRepository(),
                    container.getInstalledAppsSource());
        }
        if (modelClass.isAssignableFrom(StatsViewModel.class)) {
            return (T) new StatsViewModel(container.getStatsRepository(),
                    container.getPetStateRepository());
        }
        if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(container.getPetStateRepository(),
                    container.getWatchedAppsRepository(), container.getSettingsRepository(),
                    container.getDoomScrollMonitor()::isPermissionGranted);
        }
        if (modelClass.isAssignableFrom(DebugViewModel.class)) {
            return (T) new DebugViewModel(container.getPetStateRepository(),
                    container.getMcpPetActionHandler(), container.getChatRepository(),
                    container.getDoomScrollMonitor(), container.getDoomScrollListener(),
                    container.getStatsRepository());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
