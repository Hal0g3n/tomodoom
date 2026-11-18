package com.example.tomodoom.ui.customise;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.tomodoom.R;

/**
 * Maps catalog item IDs to their rendered thumbnails. The PNGs are generated from the pet's own
 * add-on models by {@code tools/maniengine/make_thumbnails.py}.
 */
final class AccessoryThumbnails {

    private AccessoryThumbnails() {
    }

    /** @return the thumbnail for {@code itemId}, or 0 if the item has none. */
    @DrawableRes
    static int forItem(@NonNull String itemId) {
        switch (itemId) {
            case "hat_cap":
                return R.drawable.thumb_hat_cap;
            case "hat_beanie":
                return R.drawable.thumb_hat_beanie;
            case "hat_crown":
                return R.drawable.thumb_hat_crown;
            case "acc_glasses":
                return R.drawable.thumb_acc_glasses;
            case "acc_scarf":
                return R.drawable.thumb_acc_scarf;
            case "acc_backpack":
                return R.drawable.thumb_acc_backpack;
            default:
                return 0;
        }
    }
}
