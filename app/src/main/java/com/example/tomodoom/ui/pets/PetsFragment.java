package com.example.tomodoom.ui.pets;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.R;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.databinding.FragmentPetsBinding;
import com.example.tomodoom.di.AppContainer;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.PetMood;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.ui.NotificationPrompt;
import com.example.tomodoom.ui.MainPagerAdapter;
import com.example.tomodoom.ui.PetStage;
import com.example.tomodoom.ui.watchedapps.WatchedAppsActivity;
import com.example.tomodoom.viewmodel.PetsViewModel;
import com.google.android.material.snackbar.Snackbar;

/**
 * Pets page (the default tab): the pet viewport (drawn by the activity's shared pet renderer,
 * {@link PetStage}, while this page is resumed), the Usage Access prompt and the health bar below
 * the pet. Customisation is its own page ({@code ui.customise.CustomiseFragment}).
 */
public class PetsFragment extends Fragment {

    /** Warnings are optional, so a denied request needs no follow-up. */
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    private FragmentPetsBinding binding;
    private PetsViewModel viewModel;
    @Nullable
    private DoomScrollMonitor doomScrollMonitor;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(requireContext()))
                .get(PetsViewModel.class);
        doomScrollMonitor = AppContainer.from(requireContext()).getDoomScrollMonitor();

        binding.usageAccessButton.setOnClickListener(v -> {
            boolean granted = doomScrollMonitor != null && doomScrollMonitor.isPermissionGranted();
            startActivity(granted
                    ? new Intent(requireContext(), WatchedAppsActivity.class)
                    : new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });
        updateUsageAccessCard();

        viewModel.getPetState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getDecayNotice().observe(getViewLifecycleOwner(), loss -> showDecayNotice());
        stage().registerViewport(MainPagerAdapter.PAGE_PETS, binding.petViewport);
    }

    @Override
    public void onDestroyView() {
        stage().detach(binding.petViewport);
        stage().unregisterViewport(binding.petViewport);
        doomScrollMonitor = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUsageAccessCard();
        if (doomScrollMonitor != null && doomScrollMonitor.isPermissionGranted()) {
            doomScrollMonitor.startFromForeground();
            requestNotificationsOnce();
        }
        stage().attach(binding.petViewport, viewModel.getPetState().getValue());
        showDecayNotice();
    }

    @Override
    public void onPause() {
        stage().detach(binding.petViewport);
        super.onPause();
    }

    // ---------------------------------------------------------------------------------------
    // State → views
    // ---------------------------------------------------------------------------------------

    private void renderState(@NonNull PetState state) {
        binding.petName.setText(state.getPetName());
        binding.petStatus.setText(statusLabel(state));
        binding.hpProgress.setMax(state.getMaxHp());
        binding.hpProgress.setProgress(state.getCurrentHp());
        binding.hpText.setText(getString(R.string.hp_format, state.getCurrentHp(), state.getMaxHp()));

        stage().render(binding.petViewport, state);
    }

    /**
     * Shows "<name> missed you: −N HP while you were away" once, and only while this page is the
     * one on screen (a hidden page stays STARTED, so the observer alone isn't enough).
     */
    private void showDecayNotice() {
        Integer loss = viewModel.getDecayNotice().getValue();
        if (binding == null || !isResumed() || loss == null || loss <= 0) {
            return;
        }
        viewModel.acknowledgeDecayNotice();
        String name = viewModel.getCurrentState().getPetName();
        Snackbar.make(binding.getRoot(),
                        getString(R.string.inactivity_decay_notice, name, loss),
                        Snackbar.LENGTH_LONG)
                .setAnchorView(binding.hpCard)
                .show();
    }

    @StringRes
    private static int statusLabel(@NonNull PetState state) {
        switch (PetMood.of(state)) {
            case FAINTED:
                return R.string.pet_status_fainted;
            case THRIVING:
                return R.string.pet_status_thriving;
            case OKAY:
                return R.string.pet_status_okay;
            case STRUGGLING:
            default:
                return R.string.pet_status_struggling;
        }
    }

    private void updateUsageAccessCard() {
        if (binding == null || doomScrollMonitor == null) {
            return;
        }
        // Before access: ask for it. After: the way into the watched-apps picker.
        boolean granted = doomScrollMonitor.isPermissionGranted();
        binding.usageAccessMessage.setText(
                granted ? R.string.usage_access_watching : R.string.usage_access_explanation);
        binding.usageAccessButton.setText(
                granted ? R.string.usage_access_choose_apps : R.string.usage_access_open_settings);
    }

    /**
     * Android 13+ needs permission for the scrolling warnings. Asks at most once per launch, and
     * not at all in the launch where onboarding already asked ({@link NotificationPrompt}).
     */
    private void requestNotificationsOnce() {
        if (!NotificationPrompt.shouldRequest(requireContext())) {
            return;
        }
        NotificationPrompt.launch(notificationPermissionLauncher);
    }

    /** The activity's shared pet renderer; this page shows the pet only while it is resumed. */
    @NonNull
    private PetStage stage() {
        return ((PetStage.Host) requireActivity()).getPetStage();
    }
}
