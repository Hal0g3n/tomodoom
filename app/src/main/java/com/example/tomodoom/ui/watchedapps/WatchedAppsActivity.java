package com.example.tomodoom.ui.watchedapps;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.databinding.ActivityWatchedAppsBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.viewmodel.WatchedAppsViewModel;

/**
 * Lets the user choose which apps doom-scroll detection watches. Opened from the Settings page's
 * Tracked apps card (onboarding shows the same list, {@link WatchedAppsList}, on a page of its
 * own). Changes save at once; Back closes it.
 */
public class WatchedAppsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityWatchedAppsBinding binding = ActivityWatchedAppsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WatchedAppsViewModel viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(WatchedAppsViewModel.class);
        WatchedAppsList.bind(this, binding.watchedApps, viewModel);
    }
}
