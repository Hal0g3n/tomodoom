package com.example.tomodoom.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure-Java maths for where {@link PetStage} draws the pet: the frame for a pager position
 * (sliding and shrinking between the Pets and Customise viewports), and the model box where drags
 * turn the pet.
 */
public final class PetTransitionMath {

    /** Without a health bar, the box ends this fraction of the viewport's height above its bottom. */
    public static final float MODEL_BOX_BOTTOM_FRACTION = 0.20f;

    private PetTransitionMath() {
    }

    /**
     * An axis-aligned rect with a corner radius, in px, and the pet's {@code lift} there (0-1: how
     * far to raise it clear of a health bar). Immutable.
     */
    public static final class Frame {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float radius;
        public final float lift;

        public Frame(float left, float top, float right, float bottom, float radius, float lift) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
            this.lift = lift;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
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

    /**
     * Where the pet waits while the pager is past Pets but the Pets page isn't laid out (a tab tap
     * that jumps several pages detaches Pets before its frame has slid away).
     *
     * @param position the pager's left page.
     * @param pets     the Pets viewport's pet area, or null if it isn't laid out.
     * @param last     the last frame sent, or null if none yet.
     * @return {@code last} moved just off the layer's left edge, as if it had slid away with Pets;
     * null when the pet needn't be parked (Pets is laid out, the pager isn't past Pets, or no frame
     * was ever sent).
     */
    @Nullable
    public static Frame parkedFrame(int position, @Nullable Frame pets, @Nullable Frame last) {
        if (pets != null || last == null || position <= MainPagerAdapter.PAGE_PETS) {
            return null;
        }
        float width = last.width();
        return new Frame(-width, last.top, 0f, last.bottom, last.radius, last.lift);
    }

    /**
     * @return whether {@code frame} lies wholly outside a layer {@code layerWidth} px wide, so
     * the pet can be hidden (a paused WebView may not redraw after its frame moves).
     */
    public static boolean isOffScreen(@NonNull Frame frame, float layerWidth) {
        return frame.right <= 0f || frame.left >= layerWidth;
    }

    /** @return the rect {@code t} of the way from {@code from} to {@code to} (t clamped to [0, 1]). */
    @NonNull
    public static Frame between(@NonNull Frame from, @NonNull Frame to, float t) {
        float k = clamp01(t);
        return new Frame(lerp(from.left, to.left, k), lerp(from.top, to.top, k),
                lerp(from.right, to.right, k), lerp(from.bottom, to.bottom, k),
                lerp(from.radius, to.radius, k), lerp(from.lift, to.lift, k));
    }

    /**
     * @param pets      the Pets viewport's pet area, or null if it isn't laid out.
     * @param customise the Customise viewport's pet area, or null if it isn't laid out.
     * @return where the pet goes at this pager scroll: between the two while only they are on
     * screen, otherwise the Pets area (sliding away with its page). Null if that one is missing.
     */
    @Nullable
    public static Frame frameFor(int position, float offset, @Nullable Frame pets,
                                 @Nullable Frame customise) {
        if (pets == null) {
            return position == MainPagerAdapter.PAGE_CUSTOMISE && offset == 0f ? customise : null;
        }
        if (customise == null || !showsOnlyCustomiseAndPets(position, offset)) {
            return pets;
        }
        return between(pets, customise, towardsCustomise(position, offset));
    }

    /**
     * @param area      the viewport's pet area.
     * @param inset     how far the box stays in from the sides (and, with a health bar, the top).
     * @param healthBar the health bar floating over the area's bottom, or null.
     * @return where one-finger drags turn the pet: lined up with the health bar (which is inside
     * it), otherwise inset from the sides and ending {@link #MODEL_BOX_BOTTOM_FRACTION} above the
     * bottom. The rest of the area is left to page swipes.
     */
    @NonNull
    public static Frame modelBox(@NonNull Frame area, float inset, @Nullable Frame healthBar) {
        if (healthBar != null) {
            return new Frame(healthBar.left, area.top + inset, healthBar.right, healthBar.bottom,
                    0f, 0f);
        }
        return new Frame(area.left + inset, area.top, area.right - inset,
                area.top + area.height() * (1f - MODEL_BOX_BOTTOM_FRACTION), 0f, 0f);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
