package com.example.tomodoom.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure-Java maths for where {@link PetStage} draws the pet: the frame for a pager position (every
 * page has a pet area, a viewport or a translucent backdrop, and the pet morphs between
 * neighbouring pages' areas with the scroll), and the model box where drags turn the pet.
 */
public final class PetTransitionMath {

    /** Without a health bar, the box ends this fraction of the viewport's height above its bottom. */
    public static final float MODEL_BOX_BOTTOM_FRACTION = 0.20f;
    /** How opaque the pet is behind a page without a pet viewport (Chat, Stats, Settings). */
    public static final float BACKDROP_PET_ALPHA = 0.2f;

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
     * @param areas    each page's pet area by page index (a viewport, or a translucent backdrop),
     *                 or null where that page isn't laid out.
     * @param last     the last frame sent, or null if none yet.
     * @return where the pet goes at this pager scroll: {@code offset} of the way from page
     * {@code position}'s area to the next page's. With only one of the two laid out, that one
     * (sliding with its page); with neither (a tab tap jumping several pages), {@code last}, so
     * the pet stays where it was and never leaves the screen.
     */
    @Nullable
    public static Frame frameFor(int position, float offset, @NonNull Frame[] areas,
                                 @Nullable Frame last) {
        Frame from = areaAt(areas, position);
        Frame to = areaAt(areas, position + 1);
        if (from != null && to != null) {
            return offset <= 0f ? from : between(from, to, offset);
        }
        if (from != null) {
            return from;
        }
        return to != null ? to : last;
    }

    @Nullable
    private static Frame areaAt(@NonNull Frame[] areas, int page) {
        return page >= 0 && page < areas.length ? areas[page] : null;
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
