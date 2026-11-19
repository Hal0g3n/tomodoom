package com.example.tomodoom.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.FragmentSettingsBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.ChatTextScale;
import com.example.tomodoom.model.MusicVolume;
import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.ui.MainPagerAdapter;
import com.example.tomodoom.ui.PetStage;
import com.example.tomodoom.ui.PetTransitionMath;
import com.example.tomodoom.ui.onboarding.PetChoiceAdapter;
import com.example.tomodoom.ui.onboarding.PetPortraits;
import com.example.tomodoom.ui.watchedapps.WatchedAppsActivity;
import com.example.tomodoom.viewmodel.SettingsViewModel;
import com.google.android.material.snackbar.Snackbar;

/**
 * Settings page: rename the pet, switch to another pet, choose the tracked apps, set the chat
 * text size and the background-music volume. Holds no logic; every change goes through {@link SettingsViewModel}. The activity's
 * pet ({@link PetStage}) fades out on this page but keeps running.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private PetChoiceAdapter petChoices;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(requireContext()))
                .get(SettingsViewModel.class);

        setUpName(savedInstanceState);
        setUpPetChoices();
        setUpApps();
        setUpChatSize();
        setUpMusic();

        viewModel.getPetState().observe(getViewLifecycleOwner(), this::renderPet);
        viewModel.getWatchedCount().observe(getViewLifecycleOwner(), this::renderWatched);
        viewModel.getChatTextScale().observe(getViewLifecycleOwner(), this::renderChatSize);
        viewModel.getMusicVolume().observe(getViewLifecycleOwner(), this::renderMusic);
        // The pet fades out here but keeps running, so it returns without a flash.
        stage().registerBackdrop(MainPagerAdapter.PAGE_SETTINGS, binding.getRoot(),
                PetTransitionMath.HIDDEN_PET_ALPHA);
        viewModel.getPetState().observe(getViewLifecycleOwner(),
                state -> stage().render(binding.getRoot(), state));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUsageAccess(); // may have changed in the system settings
        viewModel.refreshInstalledApps(); // apps may have been installed or removed meanwhile
        stage().attach(binding.getRoot(), viewModel.getPetState().getValue());
    }

    @Override
    public void onPause() {
        stage().detach(binding.getRoot());
        super.onPause();
    }

    /** The activity's shared pet renderer, hidden (alpha 0) on this page. */
    @NonNull
    private PetStage stage() {
        return ((PetStage.Host) requireActivity()).getPetStage();
    }

    @Override
    public void onDestroyView() {
        stage().detach(binding.getRoot());
        stage().unregisterBackdrop(binding.getRoot());
        super.onDestroyView();
        binding = null;
        petChoices = null;
    }

    // ---- Name ----

    private void setUpName(@Nullable Bundle savedInstanceState) {
        binding.petNameLayout.setCounterMaxLength(PetNames.MAX_LENGTH);
        if (savedInstanceState == null) {
            // On a restore the field keeps what the user typed.
            binding.petNameInput.setText(viewModel.getCurrentState().getPetName());
        }
        binding.petNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateNameControls();
            }
        });
        binding.petNameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveName();
                return true;
            }
            return false;
        });
        binding.petNameSave.setOnClickListener(v -> saveName());
    }

    @NonNull
    private String nameInput() {
        CharSequence text = binding.petNameInput.getText();
        return text != null ? text.toString() : "";
    }

    private void updateNameControls() {
        if (binding == null) {
            return;
        }
        String input = nameInput();
        binding.petNameSave.setEnabled(viewModel.canSaveName(input));
        binding.petNameLayout.setError(PetNames.isValid(input) ? null
                : getString(R.string.onboarding_name_error, PetNames.MAX_LENGTH));
    }

    private void saveName() {
        if (!viewModel.canSaveName(nameInput())) {
            return;
        }
        String saved = viewModel.saveName(nameInput());
        if (saved == null) {
            return;
        }
        binding.petNameInput.setText(saved);
        binding.petNameInput.clearFocus();
        hideKeyboard();
        showMessage(getString(R.string.settings_name_saved, saved));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.petNameInput.getWindowToken(), 0);
        }
    }

    // ---- Species ----

    private void setUpPetChoices() {
        petChoices = new PetChoiceAdapter(viewModel.getSpecies(), species -> {
            if (viewModel.selectSpecies(species)) {
                petChoices.setSelected(species);
                int speciesName = PetPortraits.nameFor(species);
                showMessage(getString(R.string.settings_pet_changed,
                        viewModel.getCurrentState().getPetName(),
                        speciesName != 0 ? getString(speciesName) : species));
            }
        });
        binding.petChoices.setAdapter(petChoices);
    }

    private void renderPet(@NonNull PetState state) {
        petChoices.setSelected(state.getPetSpecies());
        updateNameControls();
    }

    // ---- Tracked apps ----

    private void setUpApps() {
        binding.chooseAppsButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), WatchedAppsActivity.class)));
        binding.usageAccessButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
    }

    /** {@code count}: watched apps that are installed, as the watched-apps picker counts them. */
    private void renderWatched(@Nullable Integer count) {
        if (count == null) {
            return;
        }
        binding.appsSummary.setText(getResources()
                .getQuantityString(R.plurals.watched_apps_summary, count, count));
    }

    private void updateUsageAccess() {
        boolean granted = viewModel.isUsageAccessGranted();
        binding.appsText.setText(granted
                ? R.string.settings_apps_text
                : R.string.settings_apps_no_access);
        binding.usageAccessButton.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    // ---- Chat text size ----

    private void setUpChatSize() {
        binding.chatSizeSlider.setValueFrom(0f);
        binding.chatSizeSlider.setValueTo(ChatTextScale.stepCount() - 1);
        binding.chatSizeSlider.setStepSize(1f);
        binding.chatSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                viewModel.setChatTextScaleIndex(Math.round(value));
            }
        });
    }

    private void renderChatSize(@Nullable Float scale) {
        float value = scale != null ? scale : ChatTextScale.DEFAULT;
        int index = ChatTextScale.indexOf(value);
        if (Math.round(binding.chatSizeSlider.getValue()) != index) {
            binding.chatSizeSlider.setValue(index);
        }
        binding.chatSizeValue.setText(
                getString(R.string.settings_chat_value, ChatTextScale.percent(value)));
        float base = getResources().getDimension(R.dimen.chat_bubble_text_size);
        binding.chatSizePreview.setTextSize(TypedValue.COMPLEX_UNIT_PX, base * value);
    }

    // ---- Background music ----

    private void setUpMusic() {
        binding.musicVolumeSlider.setValueFrom(MusicVolume.MIN);
        binding.musicVolumeSlider.setValueTo(MusicVolume.MAX);
        binding.musicVolumeSlider.setStepSize(MusicVolume.STEP);
        binding.musicVolumeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                viewModel.setMusicVolume(Math.round(value));
            }
        });
    }

    private void renderMusic(@Nullable Integer volume) {
        int percent = volume != null ? volume : MusicVolume.DEFAULT;
        if (Math.round(binding.musicVolumeSlider.getValue()) != percent) {
            binding.musicVolumeSlider.setValue(percent);
        }
        binding.musicVolumeValue.setText(percent == 0
                ? getString(R.string.settings_music_off)
                : getString(R.string.settings_music_value, percent));
    }

    // ---- Shared ----

    private void showMessage(@NonNull String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT);
        View navView = requireActivity().findViewById(R.id.nav_view);
        if (navView != null) {
            snackbar.setAnchorView(navView); // above the bottom bar, not over it
        }
        snackbar.show();
    }
}
