package com.example.tomodoom.ui.pets;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tomodoom.BuildConfig;
import com.example.tomodoom.R;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.databinding.FragmentPetsBinding;
import com.example.tomodoom.di.AppContainer;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.AccessoryItem;
import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.DoomScrollEvent;
import com.example.tomodoom.model.PetMood;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.ui.PageSwipeHost;
import com.example.tomodoom.viewmodel.PetsViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

/**
 * Pets page: HP header, the pet viewport (drawn entirely by {@link ManiengineAssetRenderer}),
 * a customise FAB and, in debug builds, a debug row.
 *
 * <p>The customisation controls (slot tabs + item chips) live in a right-side panel: the END
 * drawer of {@code pets_drawer}. It opens only from the FAB (edge-swipe is locked off while it is
 * closed) and closes via its close button, a scrim tap, a swipe, or Back.</p>
 */
public class PetsFragment extends Fragment {

    private FragmentPetsBinding binding;
    private PetsViewModel viewModel;
    @Nullable
    private DoomScrollMonitor doomScrollMonitor;
    @Nullable
    private ManiengineAssetRenderer renderer;
    /** Slot whose chips are currently shown. */
    @Nullable
    private String chipSlot;
    /** Closes the customisation panel on Back; enabled only while the panel is open. */
    private final OnBackPressedCallback closePanelOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            closeCustomisePanel();
        }
    };

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

        setUpCustomisePanel();
        setUpSlotTabs();
        setUpDebugPanel();
        binding.usageAccessButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        updateUsageAccessCard();

        viewModel.getSelectedSlot().observe(getViewLifecycleOwner(), this::showSlot);
        viewModel.getPetState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // DrawerLayout restores an open panel without notifying its DrawerListener.
        syncCustomisePanelState();
    }

    @Override
    public void onDestroyView() {
        if (renderer != null) {
            renderer.release(binding.petViewport);
            renderer = null;
        }
        chipSlot = null;
        doomScrollMonitor = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUsageAccessCard();
        syncCustomisePanelState();
        if (renderer != null) {
            renderer.resume(binding.petViewport);
        }
    }

    @Override
    public void onPause() {
        // Leaving the page (swipe, bottom nav or app switch): page swipes must work again even if
        // the panel is still open, or the user couldn't swipe back to Pets.
        setPageSwipeEnabled(true);
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
        syncChipSelection(state);
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
    // Customisation panel (END drawer)
    // ---------------------------------------------------------------------------------------

    private void setUpCustomisePanel() {
        DrawerLayout drawer = binding.petsDrawer;
        drawer.setDrawerTitle(GravityCompat.END, getString(R.string.customise_title));
        // Swallow taps on empty panel space so they don't reach the content beneath the drawer.
        // Set here rather than in XML to avoid lint's KeyboardInaccessibleWidget on the root.
        binding.customisePanel.getRoot().setClickable(true);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                syncCustomisePanelState();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                syncCustomisePanelState();
            }
        });
        binding.customiseFab.setOnClickListener(v -> openCustomisePanel());
        binding.customisePanel.customiseClose.setOnClickListener(v -> closeCustomisePanel());
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), closePanelOnBack);
    }

    private void openCustomisePanel() {
        binding.petsDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END);
        binding.petsDrawer.openDrawer(GravityCompat.END);
        closePanelOnBack.setEnabled(true); // Back also works during the open animation
    }

    private void closeCustomisePanel() {
        if (binding != null) {
            binding.petsDrawer.closeDrawer(GravityCompat.END);
        }
    }

    /**
     * Open: unlocked (swipe/scrim close it) and Back closes it. Closed: locked so it only opens
     * from the FAB (no edge-swipe or peek), and Back falls through to navigation. Page swipes
     * (ViewPager2) are suspended while it is open and this page is resumed.
     */
    private void syncCustomisePanelState() {
        if (binding == null) {
            return;
        }
        boolean open = binding.petsDrawer.isDrawerOpen(GravityCompat.END);
        binding.petsDrawer.setDrawerLockMode(open
                ? DrawerLayout.LOCK_MODE_UNLOCKED
                : DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
        closePanelOnBack.setEnabled(open);
        // While the panel is open, horizontal drags belong to it (swipe-to-close), not the pager.
        if (isResumed()) {
            setPageSwipeEnabled(!open);
        }
    }

    private void setPageSwipeEnabled(boolean enabled) {
        if (getActivity() instanceof PageSwipeHost) {
            ((PageSwipeHost) getActivity()).setPageSwipeEnabled(enabled);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Customisation controls (inside the panel)
    // ---------------------------------------------------------------------------------------

    private void setUpSlotTabs() {
        TabLayout tabs = binding.customisePanel.slotTabs;
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
        TabLayout tabs = binding.customisePanel.slotTabs;
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
        binding.customisePanel.itemChips.removeAllViews();
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
                .inflate(R.layout.item_accessory_chip, binding.customisePanel.itemChips, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(itemId);
        chip.setOnClickListener(v -> viewModel.equipItem(slot, itemId));
        binding.customisePanel.itemChips.addView(chip);
    }

    /** Checks the chip matching the equipped item for the visible slot ("None" when empty). */
    private void syncChipSelection(@NonNull PetState state) {
        if (chipSlot == null) {
            return;
        }
        String equipped = state.getEquippedItem(chipSlot);
        for (int i = 0; i < binding.customisePanel.itemChips.getChildCount(); i++) {
            View child = binding.customisePanel.itemChips.getChildAt(i);
            Object tag = child.getTag();
            boolean matches = equipped == null ? tag == null : equipped.equals(tag);
            if (matches) {
                binding.customisePanel.itemChips.check(child.getId());
                return;
            }
        }
    }

    @StringRes
    private static int slotLabel(@NonNull String slot) {
        switch (slot) {
            case AccessorySlot.HAT:
                return R.string.slot_hat;
            case AccessorySlot.OUTFIT:
                return R.string.slot_outfit;
            case AccessorySlot.ACCESSORY:
                return R.string.slot_accessory;
            default:
                throw new IllegalArgumentException("No label for slot " + slot);
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
