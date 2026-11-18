package com.example.tomodoom.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.MainActivity;
import com.example.tomodoom.R;
import com.example.tomodoom.databinding.ActivityOnboardingBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.PetNames;
import com.example.tomodoom.viewmodel.OnboardingViewModel;

/**
 * Launcher activity. On the first launch it asks the user to name their pet, then opens
 * {@link MainActivity}. Once onboarding is complete it forwards straight to
 * {@link MainActivity} without showing anything.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private OnboardingViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(OnboardingViewModel.class);
        if (viewModel.isOnboardingComplete()) {
            openMainAndFinish();
            return;
        }

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            openMainAndFinish();
        } else {
            binding.petNameLayout.setError(
                    getString(R.string.onboarding_name_error, PetNames.MAX_LENGTH));
        }
    }

    private String currentInput() {
        Editable text = binding.petNameInput.getText();
        return text != null ? text.toString() : "";
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
