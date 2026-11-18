package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable snapshot of the virtual pet's state.
 *
 * <p>Instances are created and published only by
 * {@link com.example.tomodoom.repository.PetStateRepository}, which is the single source of
 * truth. Consumers must never cache an instance and assume it is still current; observe
 * {@code PetStateRepository#getPetState()} or call {@code getCurrentState()} instead.</p>
 *
 * <p>Invariants (enforced by the constructor):</p>
 * <ul>
 *     <li>{@code maxHp >= 1}</li>
 *     <li>{@code 0 <= currentHp <= maxHp} (out-of-range values are clamped)</li>
 *     <li>{@code equippedItems} contains no null/empty keys or values; it maps an
 *     {@link AccessorySlot} ID to an {@link AccessoryItem} ID.</li>
 * </ul>
 *
 * <p>Pure Java (no {@code android.*} imports), so it is unit-testable on the JVM.</p>
 */
public final class PetState {

    public static final String DEFAULT_PET_NAME = "Tomo";
    public static final int DEFAULT_MAX_HP = 100;
    /** Starts below max so HP rewards are visible immediately. */
    public static final int DEFAULT_START_HP = 50;

    @NonNull
    private final String petName;
    private final int currentHp;
    private final int maxHp;
    @NonNull
    private final Map<String, String> equippedItems;

    public PetState(@NonNull String petName,
                    int currentHp,
                    int maxHp,
                    @Nullable Map<String, String> equippedItems) {
        this.petName = petName;
        this.maxHp = Math.max(1, maxHp);
        this.currentHp = clamp(currentHp, 0, this.maxHp);
        TreeMap<String, String> copy = new TreeMap<>();
        if (equippedItems != null) {
            for (Map.Entry<String, String> e : equippedItems.entrySet()) {
                if (!isNullOrEmpty(e.getKey()) && !isNullOrEmpty(e.getValue())) {
                    copy.put(e.getKey(), e.getValue());
                }
            }
        }
        this.equippedItems = Collections.unmodifiableMap(copy);
    }

    /** A fresh pet: {@link #DEFAULT_PET_NAME}, {@link #DEFAULT_START_HP}/{@link #DEFAULT_MAX_HP}, nothing equipped. */
    @NonNull
    public static PetState createDefault() {
        return new PetState(DEFAULT_PET_NAME, DEFAULT_START_HP, DEFAULT_MAX_HP, null);
    }

    @NonNull
    public String getPetName() {
        return petName;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    /** @return HP as a fraction in {@code [0, 1]}. */
    public float getHpFraction() {
        return (float) currentHp / (float) maxHp;
    }

    /** @return true when HP has reached zero. */
    public boolean isFainted() {
        return currentHp == 0;
    }

    /** @return unmodifiable map of slot ID to equipped item ID (sorted by slot ID). */
    @NonNull
    public Map<String, String> getEquippedItems() {
        return equippedItems;
    }

    /** @return the item ID equipped in {@code slot}, or {@code null} if the slot is empty. */
    @Nullable
    public String getEquippedItem(@NonNull String slot) {
        return equippedItems.get(slot);
    }

    /** @return a copy with the pet's name set to {@code name} (see {@link PetNames} for validation). */
    @NonNull
    public PetState withName(@NonNull String name) {
        return new PetState(name, currentHp, maxHp, equippedItems);
    }

    /** @return a copy with HP set to {@code hp}, clamped to {@code [0, maxHp]}. */
    @NonNull
    public PetState withHp(int hp) {
        return new PetState(petName, hp, maxHp, equippedItems);
    }

    /**
     * @param slot   slot ID (see {@link AccessorySlot}); must be non-empty.
     * @param itemId item ID to equip, or {@code null}/empty to unequip the slot.
     * @return a copy with the slot updated.
     */
    @NonNull
    public PetState withEquippedItem(@NonNull String slot, @Nullable String itemId) {
        if (isNullOrEmpty(slot)) {
            throw new IllegalArgumentException("slot must be non-empty");
        }
        TreeMap<String, String> copy = new TreeMap<>(equippedItems);
        if (isNullOrEmpty(itemId)) {
            copy.remove(slot);
        } else {
            copy.put(slot, itemId);
        }
        return new PetState(petName, currentHp, maxHp, copy);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isNullOrEmpty(@Nullable String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PetState)) return false;
        PetState other = (PetState) o;
        return currentHp == other.currentHp
                && maxHp == other.maxHp
                && petName.equals(other.petName)
                && equippedItems.equals(other.equippedItems);
    }

    @Override
    public int hashCode() {
        int result = petName.hashCode();
        result = 31 * result + currentHp;
        result = 31 * result + maxHp;
        result = 31 * result + equippedItems.hashCode();
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "PetState{name=" + petName + ", hp=" + currentHp + "/" + maxHp
                + ", equipped=" + equippedItems + "}";
    }
}
