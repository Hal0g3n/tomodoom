package com.example.tomodoom.ui.onboarding;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.tomodoom.R;

/**
 * Maps {@link com.example.tomodoom.model.PetSpecies} IDs to their onboarding portrait and display
 * name. The portraits are rendered from the pet models by {@code tools/maniengine/make_thumbnails.py}.
 */
public final class PetPortraits {

    private PetPortraits() {
    }

    /** @return the portrait for {@code species}, or 0 if it has none. */
    @DrawableRes
    public static int portraitFor(@NonNull String species) {
        switch (species) {
            case "rabbit":
                return R.drawable.thumb_pet_rabbit;
            case "cat":
                return R.drawable.thumb_pet_cat;
            default:
                return 0;
        }
    }

    /** @return the display name for {@code species}, or 0 if it has none. */
    @StringRes
    public static int nameFor(@NonNull String species) {
        switch (species) {
            case "rabbit":
                return R.string.pet_species_rabbit;
            case "cat":
                return R.string.pet_species_cat;
            default:
                return 0;
        }
    }
}
