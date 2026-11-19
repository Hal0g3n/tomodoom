package com.example.tomodoom.ui.watchedapps;

import android.content.Context;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.ViewWatchedAppsListBinding;
import com.example.tomodoom.model.InstalledApp;
import com.example.tomodoom.model.WatchedApps;
import com.example.tomodoom.viewmodel.WatchedAppsViewModel;

import java.util.List;
import java.util.Set;

/**
 * Binds {@code view_watched_apps_list.xml} (one switch per launcher app, a summary, loading and
 * empty states) to a {@link WatchedAppsViewModel}. Shared by {@link WatchedAppsActivity} and
 * onboarding's tracked-apps page. Toggles save at once through the ViewModel.
 */
public final class WatchedAppsList {

    @NonNull
    private final ViewWatchedAppsListBinding binding;
    @NonNull
    private final WatchedAppsViewModel viewModel;
    @NonNull
    private final WatchedAppsAdapter adapter;

    private WatchedAppsList(@NonNull ViewWatchedAppsListBinding binding,
                            @NonNull WatchedAppsViewModel viewModel) {
        this.binding = binding;
        this.viewModel = viewModel;
        Context context = binding.getRoot().getContext();
        this.adapter = new WatchedAppsAdapter(context.getPackageManager(), viewModel::setWatched);
    }

    /** Sets up the list and starts observing {@code viewModel} for {@code owner}'s lifetime. */
    @MainThread
    public static void bind(@NonNull LifecycleOwner owner,
                            @NonNull ViewWatchedAppsListBinding binding,
                            @NonNull WatchedAppsViewModel viewModel) {
        WatchedAppsList list = new WatchedAppsList(binding, viewModel);
        binding.watchedAppsList.setLayoutManager(
                new LinearLayoutManager(binding.getRoot().getContext()));
        binding.watchedAppsList.setAdapter(list.adapter);
        viewModel.getApps().observe(owner, list::showApps);
        viewModel.getWatchedPackages().observe(owner, list::showWatched);
    }

    private void showApps(@Nullable List<InstalledApp> apps) {
        if (apps == null) {
            return;
        }
        binding.watchedAppsLoading.setVisibility(View.GONE);
        binding.watchedAppsList.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
        binding.watchedAppsEmpty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.setApps(apps);
        Set<String> watched = viewModel.getWatchedPackages().getValue();
        if (watched != null) {
            showWatched(watched);
        }
    }

    private void showWatched(@Nullable Set<String> watched) {
        if (watched == null) {
            return;
        }
        adapter.setWatched(watched);
        List<InstalledApp> apps = viewModel.getLoadedApps();
        if (apps == null) {
            binding.watchedAppsSummary.setVisibility(View.INVISIBLE);
            return;
        }
        int count = WatchedApps.countWatched(apps, watched);
        binding.watchedAppsSummary.setVisibility(View.VISIBLE);
        binding.watchedAppsSummary.setText(binding.getRoot().getResources()
                .getQuantityString(R.plurals.watched_apps_summary, count, count));
    }
}
