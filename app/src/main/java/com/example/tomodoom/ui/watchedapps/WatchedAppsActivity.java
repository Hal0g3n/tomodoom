package com.example.tomodoom.ui.watchedapps;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.ActivityWatchedAppsBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.InstalledApp;
import com.example.tomodoom.model.WatchedApps;
import com.example.tomodoom.viewmodel.WatchedAppsViewModel;

import java.util.List;
import java.util.Set;

/**
 * Lets the user choose which apps doom-scroll detection watches. Opened from the Pets page's
 * Usage Access card. Changes save at once; Back closes it.
 */
public class WatchedAppsActivity extends AppCompatActivity {

    private ActivityWatchedAppsBinding binding;
    private WatchedAppsViewModel viewModel;
    private WatchedAppsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWatchedAppsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(WatchedAppsViewModel.class);
        adapter = new WatchedAppsAdapter(getPackageManager(), viewModel::setWatched);
        binding.watchedAppsList.setLayoutManager(new LinearLayoutManager(this));
        binding.watchedAppsList.setAdapter(adapter);

        viewModel.getApps().observe(this, this::showApps);
        viewModel.getWatchedPackages().observe(this, this::showWatched);
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
        binding.watchedAppsSummary.setText(
                getResources().getQuantityString(R.plurals.watched_apps_summary, count, count));
    }
}
