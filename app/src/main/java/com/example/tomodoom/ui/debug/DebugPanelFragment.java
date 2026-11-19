package com.example.tomodoom.ui.debug;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.BuildConfig;
import com.example.tomodoom.R;
import com.example.tomodoom.databinding.FragmentDebugPanelBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.DoomScrollRate;
import com.example.tomodoom.model.PetMood;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.ui.AppReset;
import com.example.tomodoom.viewmodel.DebugViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Map;

/**
 * Hidden debug panel, a bottom sheet opened by long-pressing the Pets tab (all builds). It shows
 * the live pet state and drives each feature through its real path; see {@link DebugViewModel}.
 */
public class DebugPanelFragment extends BottomSheetDialogFragment {

    private static final String TAG = "debug_panel";

    private FragmentDebugPanelBinding binding;
    private DebugViewModel viewModel;

    /** Shows the panel unless it is already open. */
    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved()) {
            new DebugPanelFragment().show(fragmentManager, TAG);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Open fully: the panel is long, and a half-open sheet hides most of it.
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDebugPanelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(requireContext()))
                .get(DebugViewModel.class);

        // Pet
        binding.hpMinus10.setOnClickListener(v -> viewModel.changeHp(-10));
        binding.hpMinus1.setOnClickListener(v -> viewModel.changeHp(-1));
        binding.hpPlus1.setOnClickListener(v -> viewModel.changeHp(1));
        binding.hpPlus10.setOnClickListener(v -> viewModel.changeHp(10));
        binding.hpSet0.setOnClickListener(v -> viewModel.setHp(0));
        binding.hpSet30.setOnClickListener(v -> viewModel.setHp(30));
        binding.hpSet50.setOnClickListener(v -> viewModel.setHp(50));
        binding.hpSet80.setOnClickListener(v -> viewModel.setHp(80));
        binding.hpSetMax.setOnClickListener(v -> viewModel.setFullHp());
        binding.unequipAll.setOnClickListener(v -> viewModel.unequipAll());

        // Doom scroll
        binding.doomRateLayout.setHelperText(getString(R.string.debug_doom_rate_helper,
                DoomScrollRate.MIN, DoomScrollRate.MAX, DoomScrollRate.DEFAULT));
        binding.doomRateApply.setOnClickListener(v -> applyDoomScrollRate());
        binding.doomRateInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyDoomScrollRate();
                return true;
            }
            return false;
        });
        viewModel.getDoomScrollHpPerMinute().observe(getViewLifecycleOwner(), this::renderRate);
        binding.doomMinute.setOnClickListener(v -> viewModel.simulateDoomScrollMinute());
        binding.doomWarning.setOnClickListener(v -> viewModel.simulateDoomScrollWarning());
        binding.usageSettings.setOnClickListener(v -> openUsageAccessSettings());

        // Chat / MCP
        binding.chatBackend.setText(chatBackendText());
        binding.quizPass.setOnClickListener(v -> viewModel.simulateQuizPass());
        binding.quizReplay.setOnClickListener(v -> viewModel.replayLastQuiz());
        binding.newConversation.setOnClickListener(v -> {
            viewModel.startNewConversation();
            Toast.makeText(requireContext(), R.string.debug_new_conversation_done,
                    Toast.LENGTH_SHORT).show();
        });

        // Stats
        binding.statsDemo.setOnClickListener(v -> {
            viewModel.fillDemoStats();
            Toast.makeText(requireContext(), R.string.debug_stats_demo_done,
                    Toast.LENGTH_SHORT).show();
        });
        binding.statsClear.setOnClickListener(v -> {
            viewModel.clearStats();
            Toast.makeText(requireContext(), R.string.debug_stats_clear_done,
                    Toast.LENGTH_SHORT).show();
        });

        // App
        binding.appVersion.setText(getString(R.string.debug_app_version,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                getString(BuildConfig.DEBUG ? R.string.debug_build_debug
                        : R.string.debug_build_release)));
        binding.reset.setOnClickListener(v -> AppReset.confirm(requireActivity()));

        viewModel.getPetState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getLastResult().observe(getViewLifecycleOwner(), result -> {
            binding.lastResult.setVisibility(result == null ? View.GONE : View.VISIBLE);
            binding.lastResult.setText(result);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Usage Access can change in Settings while the panel is open.
        binding.doomStatus.setText(getString(R.string.debug_doom_status,
                yesNo(viewModel.isUsageAccessGranted()), yesNo(viewModel.isMonitoring())));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void renderState(@Nullable PetState state) {
        if (state == null) {
            return;
        }
        binding.petSummary.setText(getString(R.string.debug_pet_summary,
                state.getPetName(), state.getPetSpecies(), state.getCurrentHp(),
                state.getMaxHp(), PetMood.of(state).getId()));
        StringBuilder equipped = new StringBuilder();
        for (Map.Entry<String, String> entry : state.getEquippedItems().entrySet()) {
            if (equipped.length() > 0) {
                equipped.append(", ");
            }
            equipped.append(entry.getKey()).append('=').append(entry.getValue());
        }
        binding.petEquipped.setText(getString(R.string.debug_pet_equipped,
                equipped.length() > 0 ? equipped.toString()
                        : getString(R.string.debug_nothing_equipped)));
    }

    private void renderRate(@Nullable Integer hpPerMinute) {
        if (hpPerMinute == null) {
            return;
        }
        binding.doomMinute.setText(getString(R.string.debug_doom_minute, hpPerMinute));
        // Don't overwrite what the user is typing.
        if (!binding.doomRateInput.hasFocus()) {
            binding.doomRateInput.setText(String.valueOf(hpPerMinute));
        }
    }

    private void applyDoomScrollRate() {
        CharSequence text = binding.doomRateInput.getText();
        Integer saved = viewModel.setDoomScrollHpPerMinute(text == null ? null : text.toString());
        if (saved == null) {
            binding.doomRateLayout.setError(getString(R.string.debug_doom_rate_invalid));
            return;
        }
        binding.doomRateLayout.setError(null);
        binding.doomRateInput.setText(String.valueOf(saved));
        binding.doomRateInput.clearFocus();
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(),
                InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.doomRateInput.getWindowToken(), 0);
        }
        Toast.makeText(requireContext(), getString(R.string.debug_doom_rate_done, saved),
                Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private String chatBackendText() {
        if (BuildConfig.GEMINI_API_URL.trim().isEmpty()) {
            return getString(R.string.debug_chat_mock);
        }
        String models = BuildConfig.GEMINI_MODELS.trim();
        return getString(R.string.debug_chat_gemini,
                models.isEmpty() ? getString(R.string.debug_chat_default_model) : models);
    }

    @NonNull
    private String yesNo(boolean value) {
        return getString(value ? R.string.debug_yes : R.string.debug_no);
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
