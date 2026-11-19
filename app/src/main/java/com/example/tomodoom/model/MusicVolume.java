package com.example.tomodoom.model;

/**
 * Background-music volume offered on the Settings page: a percentage from {@link #MIN} (silent)
 * to {@link #MAX} in steps of {@link #STEP}. Pure Java.
 */
public final class MusicVolume {

    public static final int MIN = 0;
    public static final int MAX = 100;
    public static final int STEP = 10;
    public static final int DEFAULT = 50;

    private MusicVolume() {
    }

    /** @return {@code percent} clamped to {@link #MIN}–{@link #MAX} and snapped to a {@link #STEP}. */
    public static int clamp(int percent) {
        if (percent <= MIN) {
            return MIN;
        }
        if (percent >= MAX) {
            return MAX;
        }
        return Math.round(percent / (float) STEP) * STEP;
    }

    /**
     * @return the player gain (0–1) for {@code percent}. Squared, so equal slider steps sound
     * roughly equally far apart.
     */
    public static float gainFor(int percent) {
        float fraction = clamp(percent) / (float) MAX;
        return fraction * fraction;
    }
}
