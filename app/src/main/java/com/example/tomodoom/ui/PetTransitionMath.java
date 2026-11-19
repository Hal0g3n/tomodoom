package com.example.tomodoom.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure-Java maths for where {@link PetStage} draws the pet: the frame for a pager position
 * (sliding and shrinking between the Pets and Customise viewports, or between Pets and the
 * translucent backdrop behind Chat), and the model box where drags turn the pet.
 */
public final class PetTransitionMath {

    /** Without a health bar, the box ends this fraction of the viewport's height above its bottom. */
    public static final float MODEL_BOX_BOTTOM_FRACTION = 0.20f;
    /** How opaque the pet is in the background of the Chat page. */
    public static final float CHAT_PET_ALPHA = 0.2f;

    private PetTransitionMath() {
    }

    /**
     * An axis-aligned rect with a corner radius, in px, the pet's {@code lift} there (0-1: how
     * far to raise it clear of a health bar) and its {@code alpha} (0-1). Immutable.
     */
    public static final class Frame {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float radius;
        public final float lift;
        public final float alpha;

        /** A fully opaque frame. */
        public Frame(float left, float top, float right, float bottom, float radius, float lift) {
            this(left, top, right, bottom, radius, lift, 1f);
        }

        public Frame(float left, float top, float right, float bottom, float radius, float lift,
                     float alpha) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = radius;
            this.lift = lift;
            this.alpha = alpha;
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
     * @return how far the pet is from the Pets viewport towards the Chat backdrop: 0 on Pets (or
     * Customise), 1 on Chat and beyond.
     */
    public static float towardsChat(int position, float offset) {
        if (position >= MainPagerAdapter.PAGE_CHAT) {
            return 1f;
        }
        return position == MainPagerAdapter.PAGE_PETS ? clamp01(offset) : 0f;
    }

    /** @return the rect {@code t} of the way from {@code from} to {@code to} (t clamped to [0, 1]). */
    @NonNull
    public static Frame between(@NonNull Frame from, @NonNull Frame to, float t) {
        float k = clamp01(t);
        return new Frame(lerp(from.left, to.left, k), lerp(from.top, to.top, k),
                lerp(from.right, to.right, k), lerp(from.bottom, to.bottom, k),
                lerp(from.radius, to.radius, k), lerp(from.lift, to.lift, k),
                lerp(from.alpha, to.alpha, k));
    }

    /** {@link #frameFor(int, float, Frame, Frame, Frame)} without a Chat backdrop. */
    @Nullable
    public static Frame frameFor(int position, float offset, @Nullable Frame pets,
                                 @Nullable Frame customise) {
        return frameFor(position, offset, pets, customise, null);
    }

    /**
     * @param pets      the Pets viewport's pet area, or null if it isn't laid out.
     * @param customise the Customise viewport's pet area, or null if it isn't laid out.
     * @param chat      the Chat backdrop's pet area (translucent), or null if it isn't laid out.
     * @return where the pet goes at this pager scroll: between Customise and Pets while only they
     * are on screen, between Pets and Chat while those two are, and the Chat area from Chat on
     * (sliding away with its page). Without the area it needs, the Pets area (sliding away with
     * its page). Null if that one is missing too.
     */
    @Nullable
    public static Frame frameFor(int position, float offset, @Nullable Frame pets,
                                 @Nullable Frame customise, @Nullable Frame chat) {
        if (chat != null && position >= MainPagerAdapter.PAGE_CHAT) {
            return chat;
        }
        if (chat != null && pets != null && position == MainPagerAdapter.PAGE_PETS
                && offset > 0f) {
            return between(pets, chat, towardsChat(position, offset));
        }
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
