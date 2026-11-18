package com.example.tomodoom.ui;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.tomodoom.R;
import com.example.tomodoom.ui.chat.ChatFragment;
import com.example.tomodoom.ui.customise.CustomiseFragment;
import com.example.tomodoom.ui.pets.PetsFragment;

/**
 * The top-level pages of {@code MainActivity}'s swipeable {@code ViewPager2}, in order. Each page
 * matches the bottom-nav menu item with the same index ({@link #menuItemIdAt}).
 *
 * <p>To add a tab: add a menu item to {@code bottom_nav_menu.xml}, then add it here in
 * {@link #MENU_ITEM_IDS} and {@link #createFragment}.</p>
 */
public final class MainPagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_CUSTOMISE = 0;
    /** The default page, shown on a fresh launch. */
    public static final int PAGE_PETS = 1;
    public static final int PAGE_CHAT = 2;

    private static final int[] MENU_ITEM_IDS = {
            R.id.navigation_customise, R.id.navigation_pets, R.id.navigation_chat};

    public MainPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case PAGE_CUSTOMISE:
                return new CustomiseFragment();
            case PAGE_CHAT:
                return new ChatFragment();
            case PAGE_PETS:
            default:
                return new PetsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return MENU_ITEM_IDS.length;
    }

    /** @return the bottom-nav menu item id for page {@code position}. */
    @IdRes
    public static int menuItemIdAt(int position) {
        return MENU_ITEM_IDS[position];
    }

    /** @return the page for bottom-nav item {@code menuItemId}, or -1 if it isn't a page. */
    public static int positionOf(@IdRes int menuItemId) {
        for (int i = 0; i < MENU_ITEM_IDS.length; i++) {
            if (MENU_ITEM_IDS[i] == menuItemId) {
                return i;
            }
        }
        return -1;
    }
}
