package com.example.tomodoom.ui;

import androidx.annotation.NonNull;

/**
 * Pure-Java maths for the pet's page transitions ({@link PetStage}), Customise ↔ Pets ↔ Chat:
 * which pager scrolls they cover, which layer holds the pet, and the rect it is drawn into
 * part-way through.
 */
public final class PetTransitionMath {

    private PetTransitionMath() {
    }

    /** An axis-aligned rect with a corner radius, in px. Immutable. */
    public static final class Frame {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float radius;

        public Frame(float left, float top, float right, float bottom, float radius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
        }

        public boolean isEmpty() {
            return right <= left || bottom <= top;
        }
    }

    /** The pet moves in the layer over the pager (Customise ↔ Pets: it shrinks into the card). */
    public static final int LAYER_FRONT = 0;
    /** The pet moves in the layer behind the pager (Pets ↔ Chat: the chat stays over it). */
    public static final int LAYER_BACK = 1;
    /** Resting exactly on Pets: either pair may be under way, so keep the current layer. */
    public static final int LAYER_KEEP = 2;

    /**
     * @param position {@code ViewPager2.OnPageChangeCallback#onPageScrolled}'s left page.
     * @param offset   its offset towards {@code position + 1}, in [0, 1).
     * @return true while only pet pages are on screen: Customise (0) and Pets (1), Pets and Chat
     * (2), or resting on one of them. Stats coming in ends the transition.
     */
    public static boolean showsOnlyPetPages(int position, float offset) {
        return position == MainPagerAdapter.PAGE_CUSTOMISE
                || position == MainPagerAdapter.PAGE_PETS
                || (position == MainPagerAdapter.PAGE_CHAT && offset == 0f);
    }

    /**
     * @return which transition layer the pet should be in: {@link #LAYER_FRONT} between Customise
     * and Pets, {@link #LAYER_BACK} between Pets and Chat, {@link #LAYER_KEEP} resting on Pets (so
     * a drag wobbling around Pets doesn't swap layers). Only meaningful while
     * {@link #showsOnlyPetPages} holds.
     */
    public static int layerFor(int position, float offset) {
        if (position == MainPagerAdapter.PAGE_CUSTOMISE) {
            return LAYER_FRONT;
        }
        if (position == MainPagerAdapter.PAGE_PETS && offset == 0f) {
            return LAYER_KEEP;
        }
        return LAYER_BACK;
    }

    /** @return the rect {@code t} of the way from {@code from} to {@code to} (t clamped to [0, 1]). */
    @NonNull
    public static Frame between(@NonNull Frame from, @NonNull Frame to, float t) {
        float k = clamp01(t);
        return new Frame(lerp(from.left, to.left, k), lerp(from.top, to.top, k),
                lerp(from.right, to.right, k), lerp(from.bottom, to.bottom, k),
                lerp(from.radius, to.radius, k));
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
