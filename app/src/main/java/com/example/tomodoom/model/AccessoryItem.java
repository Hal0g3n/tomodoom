package com.example.tomodoom.model;

import androidx.annotation.NonNull;

/**
 * A wearable item that can be equipped into one {@link AccessorySlot}.
 *
 * <p>{@link #getId()} is persisted and used by the renderer to look up assets, so it must be
 * stable. {@link #getDisplayName()} is UI-only.</p>
 */
public final class AccessoryItem {

    @NonNull
    private final String id;
    @NonNull
    private final String slot;
    @NonNull
    private final String displayName;

    public AccessoryItem(@NonNull String id, @NonNull String slot, @NonNull String displayName) {
        this.id = id;
        this.slot = slot;
        this.displayName = displayName;
    }

    @NonNull
    public String getId() {
        return id;
    }

    /** @return the {@link AccessorySlot} ID this item fits into. */
    @NonNull
    public String getSlot() {
        return slot;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessoryItem)) return false;
        AccessoryItem other = (AccessoryItem) o;
        return id.equals(other.id) && slot.equals(other.slot) && displayName.equals(other.displayName);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + slot.hashCode();
        result = 31 * result + displayName.hashCode();
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "AccessoryItem{" + id + " @" + slot + "}";
    }
}
