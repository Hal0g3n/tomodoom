package com.example.tomodoom.model;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stable string IDs for the customisation slots.
 *
 * <p>These IDs are persisted in SharedPreferences (key {@code equipped.<slotId>}) and passed over
 * the MCP bridge ({@code equip_item.slot}), so they must never be renamed once shipped.</p>
 */
public final class AccessorySlot {

    public static final String HAT = "hat";
    public static final String ACCESSORY = "accessory";

    /** All slots, in UI display order. */
    public static final List<String> ALL =
            Collections.unmodifiableList(Arrays.asList(HAT, ACCESSORY));

    private AccessorySlot() {
    }

    /** @return true if {@code slot} is one of {@link #ALL}. */
    public static boolean isValid(@Nullable String slot) {
        return slot != null && ALL.contains(slot);
    }
}
