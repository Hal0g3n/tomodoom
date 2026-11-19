package com.example.tomodoom.model;

import androidx.annotation.Nullable;

/**
 * HP the pet loses per counted minute of doom-scrolling, set in the debug panel: a whole number
 * from {@link #MIN} (no penalty) to {@link #MAX}, {@link #DEFAULT} by default. Pure Java.
 */
public final class DoomScrollRate {

    public static final int MIN = 0;
    public static final int MAX = 100;
    public static final int DEFAULT = 1;

    private DoomScrollRate() {
    }

    /** @return {@code hpPerMinute} clamped to {@link #MIN}–{@link #MAX}. */
    public static int clamp(int hpPerMinute) {
        return Math.max(MIN, Math.min(MAX, hpPerMinute));
    }

    /**
     * Parses user input such as {@code " 5 "}.
     *
     * @return the clamped rate, or {@code null} if {@code input} isn't a whole number.
     */
    @Nullable
    public static Integer parse(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return clamp(Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            // Too many digits for an int: still a number, so clamp by sign.
            if (trimmed.matches("[+-]?\\d+")) {
                return trimmed.startsWith("-") ? MIN : MAX;
            }
            return null;
        }
    }
}
