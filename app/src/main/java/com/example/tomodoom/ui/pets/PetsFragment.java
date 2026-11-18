package com.example.tomodoom.ui.pets;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.BuildConfig;
import com.example.tomodoom.R;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.databinding.FragmentPetsBinding;
import com.example.tomodoom.di.AppContainer;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.DoomScrollEvent;
import com.example.tomodoom.model.PetMood;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.viewmodel.PetsViewModel;

/**
 * Pets page (the default tab): HP header, the Usage Access prompt, the pet viewport (drawn
 * entirely by {@link ManiengineAssetRenderer}) and, in debug builds, a debug row. Customisation
 * is its own page ({@code ui.customise.CustomiseFragment}).
 */
public class PetsFragment extends Fragment {

    private FragmentPetsBinding binding;
    private PetsViewModel viewModel;
    @Nullable
    private DoomScrollMonitor doomScrollMonitor;
    @Nullable
    private ManiengineAssetRenderer renderer;

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
        renderer = AppContainer.from(requireContext()).createAssetRenderer();
        doomScrollMonitor = AppContainer.from(requireContext()).getDoomScrollMonitor();

        setUpDebugPanel();
        binding.usageAccessButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        updateUsageAccessCard();

        viewModel.getPetState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onDestroyView() {
        if (renderer != null) {
            renderer.release(binding.petViewport);
            renderer = null;
        }
        doomScrollMonitor = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUsageAccessCard();
        if (renderer != null) {
            renderer.resume(binding.petViewport);
        }
    }

    @Override
    public void onPause() {
        if (renderer != null) {
            renderer.pause(binding.petViewport);
        }
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

        if (renderer != null) {
            renderer.render(binding.petViewport, state);
        }
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

    // ---------------------------------------------------------------------------------------
    // Debug
    // ---------------------------------------------------------------------------------------

    private void setUpDebugPanel() {
        binding.debugPanel.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        binding.debugDoomScroll.setText(
                getString(R.string.debug_simulate_doom_scroll, DoomScrollEvent.DEFAULT_HP_PENALTY));
        binding.debugDoomScroll.setOnClickListener(v -> viewModel.debugSimulateDoomScroll());
        binding.debugReset.setOnClickListener(v -> viewModel.debugResetPet());
    }

    private void updateUsageAccessCard() {
        if (binding == null || doomScrollMonitor == null) {
            return;
        }
        boolean permissionGranted = doomScrollMonitor.isPermissionGranted();
        binding.usageAccessCard.setVisibility(permissionGranted ? View.GONE : View.VISIBLE);
        if (permissionGranted) {
            doomScrollMonitor.startMonitoring();
        }
    }
}
