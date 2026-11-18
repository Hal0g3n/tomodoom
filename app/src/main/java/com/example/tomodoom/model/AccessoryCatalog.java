package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Catalog of equippable items.
 *
 * <p><b>PLACEHOLDER (owned by the Maniengine Agent):</b> the items below are hard-coded so the
 * customisation UI works end-to-end. The Maniengine Agent should replace this list with one
 * backed by the real asset manifest, keeping item IDs stable once shipped because they are
 * persisted.</p>
 */
public final class AccessoryCatalog {

    private static final List<AccessoryItem> ITEMS = Collections.unmodifiableList(Arrays.asList(
            new AccessoryItem("hat_cap", AccessorySlot.HAT, "Cap"),
            new AccessoryItem("hat_beanie", AccessorySlot.HAT, "Beanie"),
            new AccessoryItem("hat_crown", AccessorySlot.HAT, "Crown"),
            new AccessoryItem("acc_glasses", AccessorySlot.ACCESSORY, "Glasses"),
            new AccessoryItem("acc_scarf", AccessorySlot.ACCESSORY, "Scarf"),
            new AccessoryItem("acc_backpack", AccessorySlot.ACCESSORY, "Backpack")
    ));

    private AccessoryCatalog() {
    }

    /** @return every item, in display order. */
    @NonNull
    public static List<AccessoryItem> getAllItems() {
        return ITEMS;
    }

    /** @return the items that fit {@code slot}, in display order (empty for unknown slots). */
    @NonNull
    public static List<AccessoryItem> getItemsForSlot(@NonNull String slot) {
        List<AccessoryItem> result = new ArrayList<>();
        for (AccessoryItem item : ITEMS) {
            if (item.getSlot().equals(slot)) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** @return the item with {@code itemId}, or {@code null} if unknown. */
    @Nullable
    public static AccessoryItem findById(@Nullable String itemId) {
        if (itemId == null) {
            return null;
        }
        for (AccessoryItem item : ITEMS) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }
}
