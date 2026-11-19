package com.example.tomodoom;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tomodoom.databinding.ActivityMainBinding;
import com.example.tomodoom.ui.AppReset;
import com.example.tomodoom.ui.MainPagerAdapter;
import com.example.tomodoom.ui.PetStage;
import com.example.tomodoom.ui.debug.DebugPanelFragment;

/**
 * Single activity hosting the three top-level pages (Customise, Pets, Chat) in a swipeable
 * {@link ViewPager2}, kept in sync with a
 * {@link com.google.android.material.bottomnavigation.BottomNavigationView}.
 *
 * <p>Pets is the default page. Swiping and tapping a bottom-nav item both change the page. Back on
 * any page other than Pets
 * returns to Pets; Back on Pets leaves the app. There is no action bar (the theme is
 * {@code NoActionBar}): each page supplies its own header.</p>
 *
 * <p>The activity owns the one {@link PetStage}: a single pet renderer that moves between the
 * Customise and Pets viewports, so only one pet is rendered at a time. Pager scrolls are forwarded
 * to it, so the pet shrinks from one viewport into the other as you swipe.</p>
 */
public class MainActivity extends AppCompatActivity implements PetStage.Host {

    private static final float NAV_ICON_SELECTED_SCALE = 1.3f;
    private static final long NAV_ICON_ANIMATION_MS = 180L;

    private ActivityMainBinding binding;
    private PetStage petStage;

    /** Back from a non-start page goes to Pets; enabled only off the Pets page. */
    private final OnBackPressedCallback backToPets = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            binding.mainPager.setCurrentItem(MainPagerAdapter.PAGE_PETS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        petStage = new PetStage(this); // before super: restored pages may ask for it
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewPager2 pager = binding.mainPager;
        pager.setAdapter(new MainPagerAdapter(this));
        // Keep both neighbours laid out, so the pet's page transition knows where it is heading
        // from the first frame of a swipe.
        pager.setOffscreenPageLimit(1);
        petStage.setTransitionLayer(binding.petTransitionLayer);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                syncWithPage(position, true);
            }

            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {
                petStage.onPagerScrolled(position, positionOffset);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                petStage.onPagerScrollStateChanged(state, pager.getCurrentItem());
            }
        });
        binding.navView.setOnItemSelectedListener(item -> {
            int position = MainPagerAdapter.positionOf(item.getItemId());
            if (position < 0) {
                return false;
            }
            if (pager.getCurrentItem() != position) {
                pager.setCurrentItem(position);
            }
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, backToPets);
        // Hidden on purpose: long-pressing the Wardrobe tab offers a full reset (testing/demos).
        View wardrobeTab = binding.navView.findViewById(R.id.navigation_customise);
        if (wardrobeTab != null) {
            wardrobeTab.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                AppReset.confirm(this);
                return true; // consumed: no tooltip
            });
        }
        // Hidden on purpose: long-pressing the Pets tab opens the debug panel (all builds).
        View petsTab = binding.navView.findViewById(R.id.navigation_pets);
        if (petsTab != null) {
            petsTab.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                DebugPanelFragment.show(getSupportFragmentManager());
                return true; // consumed: no tooltip
            });
        }
        if (savedInstanceState == null) {
            // Fresh launch: start on Pets (the middle page). A restore keeps the saved page.
            pager.setCurrentItem(MainPagerAdapter.PAGE_PETS, false);
            syncWithPage(MainPagerAdapter.PAGE_PETS, false);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // ViewPager2 restores its page without calling onPageSelected.
        syncWithPage(binding.mainPager.getCurrentItem(), false);
    }

    @Override
    protected void onDestroy() {
        // The pages' views are gone or going; free the WebView with this activity (rotation too).
        petStage.release();
        super.onDestroy();
    }

    @NonNull
    @Override
    public PetStage getPetStage() {
        return petStage;
    }

    private void syncWithPage(int position, boolean animate) {
        int selectedId = MainPagerAdapter.menuItemIdAt(position);
        binding.navView.setSelectedItemId(selectedId);
        emphasiseNavIcon(selectedId, animate);
        backToPets.setEnabled(position != MainPagerAdapter.PAGE_PETS);
    }

    /** Enlarges and lifts the open tab's icon, and returns the other icons to normal. */
    private void emphasiseNavIcon(int selectedId, boolean animate) {
        float lift = getResources().getDimension(R.dimen.nav_icon_lift);
        Menu menu = binding.navView.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            int itemId = menu.getItem(i).getItemId();
            View item = binding.navView.findViewById(itemId);
            ImageView icon = item == null ? null : findIcon(item);
            if (icon == null) {
                continue;
            }
            keepOverflowVisible(icon);
            boolean selected = itemId == selectedId;
            float scale = selected ? NAV_ICON_SELECTED_SCALE : 1f;
            float translationY = selected ? -lift : 0f;
            icon.animate().cancel();
            if (animate) {
                icon.animate().scaleX(scale).scaleY(scale).translationY(translationY)
                        .setDuration(NAV_ICON_ANIMATION_MS)
                        .setInterpolator(new OvershootInterpolator());
            } else {
                icon.setScaleX(scale);
                icon.setScaleY(scale);
                icon.setTranslationY(translationY);
            }
        }
    }

    /** The item's icon; found by type because Material's view id is private to the library. */
    @Nullable
    private static ImageView findIcon(@NonNull View view) {
        if (view instanceof ImageView) {
            return (ImageView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView icon = findIcon(group.getChildAt(i));
                if (icon != null) {
                    return icon;
                }
            }
        }
        return null;
    }

    /** Stops the icon's parents, up to the bar, from cutting off the enlarged icon. */
    private void keepOverflowVisible(@NonNull View icon) {
        ViewParent parent = icon.getParent();
        while (parent instanceof ViewGroup) {
            ((ViewGroup) parent).setClipChildren(false);
            ((ViewGroup) parent).setClipToPadding(false);
            if (parent == binding.navView) {
                return;
            }
            parent = parent.getParent();
        }
    }
}
