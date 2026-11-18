package com.example.tomodoom.ui.customise;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.FragmentCustomiseBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.AccessoryItem;
import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.ui.PetStage;
import com.example.tomodoom.viewmodel.CustomiseViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

/**
 * Customise page (leftmost tab): a live pet preview above the slot tabs and item chips. The
 * preview is the activity's single shared pet ({@link PetStage}), shown here while this page is
 * resumed. Equipping goes through {@link CustomiseViewModel} to {@code PetStateRepository}, so the
 * Pets page updates too.
 */
public class CustomiseFragment extends Fragment {

    private FragmentCustomiseBinding binding;
    private CustomiseViewModel viewModel;
    /** Slot whose chips are currently shown. */
    @Nullable
    private String chipSlot;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomiseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(requireContext()))
                .get(CustomiseViewModel.class);

        setUpSlotTabs();
        viewModel.getSelectedSlot().observe(getViewLifecycleOwner(), this::showSlot);
        viewModel.getPetState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onResume() {
        super.onResume();
        stage().attach(binding.petViewport, viewModel.getPetState().getValue());
    }

    @Override
    public void onPause() {
        stage().detach(binding.petViewport);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stage().detach(binding.petViewport);
        chipSlot = null;
        super.onDestroyView();
        binding = null;
    }

    private void renderState(@NonNull PetState state) {
        stage().render(binding.petViewport, state);
        syncChipSelection(state);
    }

    // ---------------------------------------------------------------------------------------
    // Slot tabs + item chips
    // ---------------------------------------------------------------------------------------

    private void setUpSlotTabs() {
        TabLayout tabs = binding.slotTabs;
        for (String slot : AccessorySlot.ALL) {
            tabs.addTab(tabs.newTab().setText(slotLabel(slot)).setTag(slot), false);
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Object slot = tab.getTag();
                if (slot instanceof String) {
                    viewModel.selectSlot((String) slot);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void showSlot(@NonNull String slot) {
        TabLayout tabs = binding.slotTabs;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            if (tab != null && slot.equals(tab.getTag()) && !tab.isSelected()) {
                tab.select();
            }
        }
        if (!slot.equals(chipSlot)) {
            rebuildChips(slot);
        }
    }

    private void rebuildChips(@NonNull String slot) {
        chipSlot = slot;
        binding.itemChips.removeAllViews();
        addItemChip(slot, null, getString(R.string.item_none));
        for (AccessoryItem item : viewModel.getItemsForSlot(slot)) {
            addItemChip(slot, item.getId(), item.getDisplayName());
        }
        PetState state = viewModel.getPetState().getValue();
        if (state != null) {
            syncChipSelection(state);
        }
    }

    private void addItemChip(@NonNull String slot, @Nullable String itemId, @NonNull String label) {
        Chip chip = (Chip) getLayoutInflater()
                .inflate(R.layout.item_accessory_chip, binding.itemChips, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(itemId);
        chip.setOnClickListener(v -> viewModel.equipItem(slot, itemId));
        binding.itemChips.addView(chip);
    }

    /** Checks the chip matching the equipped item for the visible slot ("None" when empty). */
    private void syncChipSelection(@NonNull PetState state) {
        if (chipSlot == null) {
            return;
        }
        String equipped = state.getEquippedItem(chipSlot);
        for (int i = 0; i < binding.itemChips.getChildCount(); i++) {
            View child = binding.itemChips.getChildAt(i);
            Object tag = child.getTag();
            boolean matches = equipped == null ? tag == null : equipped.equals(tag);
            if (matches) {
                binding.itemChips.check(child.getId());
                return;
            }
        }
    }

    @StringRes
    private static int slotLabel(@NonNull String slot) {
        switch (slot) {
            case AccessorySlot.HAT:
                return R.string.slot_hat;
            case AccessorySlot.ACCESSORY:
                return R.string.slot_accessory;
            default:
                throw new IllegalArgumentException("No label for slot " + slot);
        }
    }

    /** The activity's shared pet renderer; this page shows the pet only while it is resumed. */
    @NonNull
    private PetStage stage() {
        return ((PetStage.Host) requireActivity()).getPetStage();
    }
}
