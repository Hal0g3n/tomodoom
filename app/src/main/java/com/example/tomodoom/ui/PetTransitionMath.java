package com.example.tomodoom.ui;

import androidx.annotation.NonNull;

/**
 * Pure-Java maths for the pet's Pets ↔ Customise page transition ({@link PetStage}): which pager
 * scrolls it covers, and the rect the pet is drawn into part-way through.
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

    /**
     * @param position {@code ViewPager2.OnPageChangeCallback#onPageScrolled}'s left page.
     * @param offset   its offset towards {@code position + 1}, in [0, 1).
     * @return true while only Customise (page 0) and Pets (page 1) are on screen. Resting exactly on
     * Pets counts, so a drag wobbling around Pets doesn't end and restart the transition.
     */
    public static boolean showsOnlyCustomiseAndPets(int position, float offset) {
        return position == MainPagerAdapter.PAGE_CUSTOMISE
                || (position == MainPagerAdapter.PAGE_PETS && offset == 0f);
    }

    /**
     * @return how far the pet is from the Pets viewport towards the Customise one: 0 on Pets, 1 on
     * Customise. Only meaningful while {@link #showsOnlyCustomiseAndPets} holds.
     */
    public static float towardsCustomise(int position, float offset) {
        return position == MainPagerAdapter.PAGE_CUSTOMISE ? clamp01(1f - offset) : 0f;
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
