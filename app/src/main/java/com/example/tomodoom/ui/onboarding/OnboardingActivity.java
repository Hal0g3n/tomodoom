package com.example.tomodoom.ui.onboarding;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.MainActivity;
import com.example.tomodoom.R;
import com.example.tomodoom.databinding.ActivityOnboardingBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.ui.NotificationPrompt;
import com.example.tomodoom.ui.watchedapps.WatchedAppsActivity;
import com.example.tomodoom.viewmodel.OnboardingPermissionFlow;
import com.example.tomodoom.viewmodel.OnboardingViewModel;

/**
 * Launcher activity. On the first launch it asks the user to choose and name their pet, then for
 * Usage Access, which apps to track (once access is granted, through
 * {@link WatchedAppsActivity}) and notifications, then opens {@link MainActivity}. Once onboarding is
 * complete it forwards straight to {@link MainActivity} without showing anything.
 *
 * <p>Naming the pet completes onboarding at once, so the permission step is shown only while this
 * activity lives; a restore of that step (rotation) keeps it instead of forwarding.</p>
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String KEY_PERMISSIONS_STEP = "permissions_step";

    private ActivityOnboardingBinding binding;
    private boolean showingPermissions;
    private OnboardingViewModel viewModel;

    /** Any answer moves the flow on; a denial just leaves the Pets fallback prompt for later. */
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                viewModel.onNotificationsHandled();
                updatePermissionStep();
            });

    /** Coming back from the watched-apps picker (it saves as it goes) finishes the apps step. */
    private final ActivityResultLauncher<Intent> watchedAppsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        viewModel.onAppsHandled();
                        updatePermissionStep();
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(OnboardingViewModel.class);
        boolean restoringPermissions = savedInstanceState != null
                && savedInstanceState.getBoolean(KEY_PERMISSIONS_STEP);
        if (viewModel.isOnboardingComplete() && !restoringPermissions) {
            openMainAndFinish();
            return;
        }

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.permissionButton.setOnClickListener(v -> onPermissionButton());
        binding.permissionSkipButton.setOnClickListener(v -> onSkipPermission());
        if (restoringPermissions) {
            showPermissionStep();
            return;
        }

        PetChoiceAdapter choices = new PetChoiceAdapter(viewModel.getSpecies(),
                viewModel::selectSpecies);
        binding.petChoices.setAdapter(choices);
        viewModel.getSelectedSpecies().observe(this, choices::setSelected);

        binding.petNameLayout.setCounterMaxLength(PetNames.MAX_LENGTH);
        if (savedInstanceState == null) {
            // On a restore the EditText brings back what the user typed.
            binding.petNameInput.setText(viewModel.getSuggestedName());
            binding.petNameInput.selectAll();
        }
        updateStartButton();
        binding.petNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.petNameLayout.setError(null);
                updateStartButton();
            }
        });
        binding.petNameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        binding.startButton.setOnClickListener(v -> submit());
    }

    private void updateStartButton() {
        binding.startButton.setEnabled(viewModel.isValidName(currentInput()));
    }

    private void submit() {
        if (viewModel.submitName(currentInput())) {
            showPermissionStep();
        } else {
            binding.petNameLayout.setError(
                    getString(R.string.onboarding_name_error, PetNames.MAX_LENGTH));
        }
    }

    private String currentInput() {
        Editable text = binding.petNameInput.getText();
        return text != null ? text.toString() : "";
    }

    // ---------------------------------------------------------------------------------------
    // Permission step: Usage Access first, then tracked apps, then notifications
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        // The user may come back from the Usage Access settings screen.
        if (showingPermissions) {
            updatePermissionStep();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PERMISSIONS_STEP, showingPermissions);
    }

    private void showPermissionStep() {
        showingPermissions = true;
        binding.nameStep.setVisibility(View.GONE);
        binding.permissionsStep.setVisibility(View.VISIBLE);
        binding.permissionsTitle.setText(
                getString(R.string.onboarding_permissions_title, viewModel.getPetName()));
        updatePermissionStep();
    }

    @NonNull
    private OnboardingPermissionFlow.Step currentStep() {
        return viewModel.currentPermissionStep(NotificationPrompt.isGrantNeeded(this));
    }

    private void updatePermissionStep() {
        if (binding == null) {
            return;
        }
        OnboardingPermissionFlow.Step step = currentStep();

        binding.usageRow.setSelected(step == OnboardingPermissionFlow.Step.USAGE_ACCESS);
        boolean usageGranted = viewModel.isUsageAccessGranted();
        setStatus(binding.usageStatus, usageGranted, viewModel.isUsageSkipped());

        // Choosing apps only matters once usage is tracked, so the row waits for the grant.
        binding.appsRow.setVisibility(usageGranted ? View.VISIBLE : View.GONE);
        binding.appsRow.setSelected(step == OnboardingPermissionFlow.Step.TRACKED_APPS);
        binding.appsStatus.setVisibility(viewModel.isAppsHandled() ? View.VISIBLE : View.GONE);

        // Below Android 13, notifications need no runtime grant, so the row is hidden.
        boolean notificationsAsked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        binding.notificationsRow.setVisibility(notificationsAsked ? View.VISIBLE : View.GONE);
        binding.notificationsRow.setSelected(step == OnboardingPermissionFlow.Step.NOTIFICATIONS);
        boolean notificationsGranted = !NotificationPrompt.isGrantNeeded(this);
        setStatus(binding.notificationsStatus, notificationsGranted,
                viewModel.isNotificationsHandled());

        switch (step) {
            case USAGE_ACCESS:
                binding.permissionButton.setText(R.string.onboarding_permission_allow_usage);
                binding.permissionSkipButton.setVisibility(View.VISIBLE);
                break;
            case TRACKED_APPS:
                binding.permissionButton.setText(R.string.onboarding_permission_choose_apps);
                binding.permissionSkipButton.setVisibility(View.VISIBLE);
                break;
            case NOTIFICATIONS:
                binding.permissionButton.setText(R.string.onboarding_permission_allow_notifications);
                binding.permissionSkipButton.setVisibility(View.VISIBLE);
                break;
            case DONE:
            default:
                binding.permissionButton.setText(R.string.onboarding_continue);
                binding.permissionSkipButton.setVisibility(View.GONE);
                break;
        }
    }

    private static void setStatus(@NonNull TextView status, boolean granted,
                                  boolean skipped) {
        if (granted) {
            status.setText(R.string.onboarding_permission_allowed);
            status.setVisibility(View.VISIBLE);
        } else if (skipped) {
            status.setText(R.string.onboarding_permission_skipped);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void onPermissionButton() {
        switch (currentStep()) {
            case USAGE_ACCESS:
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                break;
            case TRACKED_APPS:
                watchedAppsLauncher.launch(new Intent(this, WatchedAppsActivity.class));
                break;
            case NOTIFICATIONS:
                NotificationPrompt.launch(notificationPermissionLauncher);
                break;
            case DONE:
            default:
                openMainAndFinish();
                break;
        }
    }

    private void onSkipPermission() {
        OnboardingPermissionFlow.Step step = currentStep();
        if (step == OnboardingPermissionFlow.Step.USAGE_ACCESS) {
            viewModel.skipUsageAccess();
        } else if (step == OnboardingPermissionFlow.Step.TRACKED_APPS) {
            // Keeps the default watched apps; Settings can change them later.
            viewModel.onAppsHandled();
        } else if (step == OnboardingPermissionFlow.Step.NOTIFICATIONS) {
            // Skipping counts as asked, so Pets doesn't prompt again in this launch.
            NotificationPrompt.markRequested();
            viewModel.onNotificationsHandled();
        }
        updatePermissionStep();
    }

    private void openMainAndFinish() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
