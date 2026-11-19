package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stable string IDs for the pet models the user can choose from during onboarding.
 *
 * <p>Each ID names a Maniengine model ({@code assets/maniengine/models/<id>.js}) and its profile in
 * the {@code PETS} table of {@code assets/maniengine/pet-scene.js}; the renderer loads the page with
 * {@code ?pet=<id>}. IDs are persisted in SharedPreferences (key {@code pet_species}), so they must
 * never be renamed once shipped.</p>
 */
public final class PetSpecies {

    public static final String RABBIT = "rabbit";
    public static final String CAT = "cat";

    /** The pet shown when nothing (or something unknown) was chosen. */
    public static final String DEFAULT = RABBIT;

    /** All species, in onboarding display order. */
    public static final List<String> ALL =
            Collections.unmodifiableList(Arrays.asList(RABBIT, CAT));

    private PetSpecies() {
    }

    /** @return true if {@code id} is one of {@link #ALL}. */
    public static boolean isValid(@Nullable String id) {
        return id != null && ALL.contains(id);
    }

    /** @return {@code id} if it is valid, otherwise {@link #DEFAULT}. */
    @NonNull
    public static String orDefault(@Nullable String id) {
        return isValid(id) ? id : DEFAULT;
    }
}
