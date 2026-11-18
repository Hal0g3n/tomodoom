package com.example.tomodoom;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tomodoom.databinding.ActivityMainBinding;
import com.example.tomodoom.ui.MainPagerAdapter;
import com.example.tomodoom.ui.PetStage;

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
 * Customise and Pets viewports, so only one pet is rendered at a time.</p>
 */
public class MainActivity extends AppCompatActivity implements PetStage.Host {

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
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                syncWithPage(position);
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
        if (savedInstanceState == null) {
            // Fresh launch: start on Pets (the middle page). A restore keeps the saved page.
            pager.setCurrentItem(MainPagerAdapter.PAGE_PETS, false);
            syncWithPage(MainPagerAdapter.PAGE_PETS);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // ViewPager2 restores its page without calling onPageSelected.
        syncWithPage(binding.mainPager.getCurrentItem());
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

    private void syncWithPage(int position) {
        binding.navView.setSelectedItemId(MainPagerAdapter.menuItemIdAt(position));
        backToPets.setEnabled(position != MainPagerAdapter.PAGE_PETS);
    }
}
