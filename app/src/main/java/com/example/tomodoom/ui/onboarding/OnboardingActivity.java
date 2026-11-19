package com.example.tomodoom.ui.onboarding;

import android.content.Intent;
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
import com.example.tomodoom.ui.watchedapps.WatchedAppsList;
import com.example.tomodoom.viewmodel.OnboardingPermissionFlow;
import com.example.tomodoom.viewmodel.OnboardingViewModel;
import com.example.tomodoom.viewmodel.WatchedAppsViewModel;

/**
 * Launcher activity. On the first launch it shows three pages: choose and name the pet; grant
 * Usage Access and notifications; choose the apps to track ({@link WatchedAppsList}). Then it opens
 * {@link MainActivity}. Once onboarding is complete it forwards straight to {@link MainActivity}
 * without showing anything.
 *
 * <p>Naming the pet completes onboarding at once, so the later pages are shown only while this
 * activity lives; a restore of one of them (rotation) keeps it instead of forwarding.</p>
 */
public class OnboardingActivity extends AppCompatActivity {

    /** Saved page: one of the {@code PAGE_*} values. */
    private static final String KEY_PAGE = "onboarding_page";
    private static final int PAGE_NAME = 0;
    private static final int PAGE_PERMISSIONS = 1;
    private static final int PAGE_APPS = 2;

    private ActivityOnboardingBinding binding;
    private int page = PAGE_NAME;
    private OnboardingViewModel viewModel;

    /** Any answer moves the flow on; a denial just leaves the Pets fallback prompt for later. */
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                viewModel.onNotificationsHandled();
                updatePermissionStep();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(OnboardingViewModel.class);
        int restoredPage = savedInstanceState != null
                ? savedInstanceState.getInt(KEY_PAGE, PAGE_NAME) : PAGE_NAME;
        if (viewModel.isOnboardingComplete() && restoredPage == PAGE_NAME) {
            openMainAndFinish();
            return;
        }

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.permissionButton.setOnClickListener(v -> onPermissionButton());
        binding.permissionSkipButton.setOnClickListener(v -> onSkipPermission());
        binding.appsContinueButton.setOnClickListener(v -> openMainAndFinish());
        if (restoredPage == PAGE_PERMISSIONS) {
            showPermissionStep();
            return;
        }
        if (restoredPage == PAGE_APPS) {
            showAppsStep();
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_PAGE, page);
    }

    // ---------------------------------------------------------------------------------------
    // Page 2, permissions: Usage Access first, then notifications
    // ---------------------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        // The user may come back from the Usage Access settings screen.
        if (page == PAGE_PERMISSIONS) {
            updatePermissionStep();
        }
    }

    private void showPermissionStep() {
        page = PAGE_PERMISSIONS;
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
        setStatus(binding.usageStatus, viewModel.isUsageAccessGranted(),
                viewModel.isUsageSkipped());

        // The row always shows. It's only an actionable step when a runtime grant is needed
        // (Android 13+, not granted); otherwise it just reports that notifications are on.
        binding.notificationsRow.setSelected(step == OnboardingPermissionFlow.Step.NOTIFICATIONS);
        boolean notificationsGranted = NotificationPrompt.areEnabled(this);
        setStatus(binding.notificationsStatus, notificationsGranted,
                viewModel.isNotificationsHandled());

        switch (step) {
            case USAGE_ACCESS:
                binding.permissionButton.setText(R.string.onboarding_permission_allow_usage);
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
            case NOTIFICATIONS:
                NotificationPrompt.launch(notificationPermissionLauncher);
                break;
            case DONE:
            default:
                showAppsStep();
                break;
        }
    }

    private void onSkipPermission() {
        OnboardingPermissionFlow.Step step = currentStep();
        if (step == OnboardingPermissionFlow.Step.USAGE_ACCESS) {
            viewModel.skipUsageAccess();
        } else if (step == OnboardingPermissionFlow.Step.NOTIFICATIONS) {
            // Skipping counts as asked, so Pets doesn't prompt again in this launch.
            NotificationPrompt.markRequested();
            viewModel.onNotificationsHandled();
        }
        updatePermissionStep();
    }

    // ---------------------------------------------------------------------------------------
    // Page 3, tracked apps: the watched-apps list (saves as it goes), then Continue
    // ---------------------------------------------------------------------------------------

    private void showAppsStep() {
        page = PAGE_APPS;
        binding.scrollSteps.setVisibility(View.GONE);
        binding.appsStep.setVisibility(View.VISIBLE);
        binding.appsSubtitle.setText(
                getString(R.string.onboarding_apps_subtitle, viewModel.getPetName()));
        // Created only now, so the installed apps load only when this page is reached.
        WatchedAppsViewModel appsViewModel = new ViewModelProvider(this,
                ViewModelFactory.from(this)).get(WatchedAppsViewModel.class);
        WatchedAppsList.bind(this, binding.appsList, appsViewModel);
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
