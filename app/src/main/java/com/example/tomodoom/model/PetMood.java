package com.example.tomodoom.model;

import androidx.annotation.NonNull;

/**
 * Coarse mood derived from HP. It is the one place the HP thresholds live: the Pets status label
 * and the Maniengine page ({@code mood} field) both use it.
 *
 * <p>Pure Java, unit-testable on the JVM.</p>
 */
public enum PetMood {
    THRIVING("thriving"),
    OKAY("okay"),
    STRUGGLING("struggling"),
    FAINTED("fainted");

    /** HP fraction at or above which the pet is {@link #THRIVING}. */
    public static final float THRIVING_FRACTION = 0.75f;
    /** HP fraction at or above which the pet is at least {@link #OKAY}. */
    public static final float OKAY_FRACTION = 0.40f;

    @NonNull
    private final String id;

    PetMood(@NonNull String id) {
        this.id = id;
    }

    /** @return a stable lower-case id (e.g. {@code "okay"}), safe to send to the renderer. */
    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public static PetMood of(@NonNull PetState state) {
        if (state.isFainted()) return FAINTED;
        float fraction = state.getHpFraction();
        if (fraction >= THRIVING_FRACTION) return THRIVING;
        if (fraction >= OKAY_FRACTION) return OKAY;
        return STRUGGLING;
    }
}
