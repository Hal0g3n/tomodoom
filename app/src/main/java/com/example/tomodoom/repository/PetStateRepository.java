package com.example.tomodoom.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tomodoom.model.PetState;

import java.util.HashMap;
import java.util.Map;

/**
 * <b>Single source of truth</b> for the pet's state, shared by the Pets UI, the chat/MCP
 * subsystem and the doom-scroll tracker. Backed by {@link SharedPreferences} and exposed
 * reactively as {@link LiveData}.
 *
 * <h3>Rules (see CLAUDE.md §3)</h3>
 * <ul>
 *     <li>Create exactly one instance (in {@code AppContainer}).</li>
 *     <li>Only this class reads or writes the {@value #PREFS_NAME} prefs file.</li>
 *     <li>All mutators are {@code synchronized} and safe to call from <b>any thread</b>.</li>
 *     <li>Publishing uses <b>only</b> {@link MutableLiveData#postValue}, always while holding the
 *     lock, and never {@code setValue}. That keeps the delivered order equal to the mutation
 *     order: an older pending post can never overwrite a newer value. As a result,
 *     {@code getPetState().getValue()} may be one main-loop iteration stale right after a
 *     mutation. Use {@link #getCurrentState()} or the mutator's return value for an
 *     immediate read.</li>
 *     <li>Disk writes use {@link SharedPreferences.Editor#apply()} (asynchronous, non-blocking).</li>
 * </ul>
 */
public final class PetStateRepository {

    private static final String TAG = "PetStateRepository";

    public static final String PREFS_NAME = "tomodoom_pet_state";
    static final String KEY_PET_NAME = "pet_name";
    static final String KEY_CURRENT_HP = "current_hp";
    static final String KEY_MAX_HP = "max_hp";
    static final String KEY_EQUIPPED_PREFIX = "equipped.";

    private final Object lock = new Object();
    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final MutableLiveData<PetState> petStateLiveData;
    /** Latest snapshot; written only under {@link #lock}, readable from any thread. */
    @NonNull
    private volatile PetState current;

    /**
     * Loads the persisted state synchronously, which is a small file read on first access.
     *
     * @param context any context; the application context is retained.
     */
    public PetStateRepository(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.current = readFromPrefs(prefs);
        // Initial value is set via the constructor (not setValue/postValue), so observers
        // receive it immediately on subscription from any thread.
        this.petStateLiveData = new MutableLiveData<>(current);
    }

    /** Observe from the UI. Emissions are delivered on the main thread. */
    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateLiveData;
    }

    /** @return the latest snapshot, synchronously, from any thread. */
    @AnyThread
    @NonNull
    public PetState getCurrentState() {
        return current;
    }

    /**
     * Increases HP by {@code amount}, clamped to {@code maxHp}.
     *
     * @param amount HP to add; must be ≥ 0.
     * @param reason short machine-readable tag for logs (e.g. {@code "quiz_passed:mock-quiz-1"}).
     * @return the new snapshot.
     * @throws IllegalArgumentException if {@code amount < 0}.
     */
    @AnyThread
    @NonNull
    public PetState increaseHp(int amount, @NonNull String reason) {
        requireNonNegative(amount);
        synchronized (lock) {
            return applyHpChange(current.getCurrentHp() + amount, "+" + amount, reason);
        }
    }

    /**
     * Decreases HP by {@code amount}, clamped to 0.
     *
     * @param amount HP to remove; must be ≥ 0.
     * @param reason short machine-readable tag for logs (e.g. {@code "doom_scroll:com.example"}).
     * @return the new snapshot.
     * @throws IllegalArgumentException if {@code amount < 0}.
     */
    @AnyThread
    @NonNull
    public PetState decreaseHp(int amount, @NonNull String reason) {
        requireNonNegative(amount);
        synchronized (lock) {
            return applyHpChange(current.getCurrentHp() - amount, "-" + amount, reason);
        }
    }

    /**
     * Equips {@code itemId} into {@code slot}, or unequips the slot when {@code itemId} is null
     * or empty. Slot/item validity against the catalog is the caller's concern; the MCP handler
     * validates input from the backend.
     *
     * @return the new snapshot.
     * @throws IllegalArgumentException if {@code slot} is empty.
     */
    @AnyThread
    @NonNull
    public PetState equipItem(@NonNull String slot, @Nullable String itemId) {
        synchronized (lock) {
            PetState next = current.withEquippedItem(slot, itemId);
            if (next.equals(current)) {
                return current;
            }
            SharedPreferences.Editor editor = prefs.edit();
            if (itemId == null || itemId.isEmpty()) {
                editor.remove(KEY_EQUIPPED_PREFIX + slot);
            } else {
                editor.putString(KEY_EQUIPPED_PREFIX + slot, itemId);
            }
            editor.apply();
            Log.d(TAG, "equip " + slot + " = " + itemId);
            publishLocked(next);
            return next;
        }
    }

    /** Wipes all persisted pet data and restores {@link PetState#createDefault()}. */
    @AnyThread
    @NonNull
    public PetState resetPetState() {
        synchronized (lock) {
            PetState fresh = PetState.createDefault();
            SharedPreferences.Editor editor = prefs.edit().clear();
            writeCore(editor, fresh);
            editor.apply();
            Log.i(TAG, "pet state reset");
            publishLocked(fresh);
            return fresh;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Internals. Every method ending in "Locked" must be called while holding `lock`.
    // ---------------------------------------------------------------------------------------

    @NonNull
    private PetState applyHpChange(int newHp, @NonNull String deltaLabel, @NonNull String reason) {
        PetState next = current.withHp(newHp);
        Log.i(TAG, "HP " + deltaLabel + " (" + reason + "): "
                + current.getCurrentHp() + " -> " + next.getCurrentHp() + "/" + next.getMaxHp());
        if (next.equals(current)) {
            return current; // e.g. already at max/zero: nothing to persist or publish
        }
        prefs.edit().putInt(KEY_CURRENT_HP, next.getCurrentHp()).apply();
        publishLocked(next);
        return next;
    }

    private void publishLocked(@NonNull PetState next) {
        current = next;
        petStateLiveData.postValue(next);
    }

    private static void writeCore(@NonNull SharedPreferences.Editor editor, @NonNull PetState state) {
        editor.putString(KEY_PET_NAME, state.getPetName())
                .putInt(KEY_CURRENT_HP, state.getCurrentHp())
                .putInt(KEY_MAX_HP, state.getMaxHp());
        for (Map.Entry<String, String> e : state.getEquippedItems().entrySet()) {
            editor.putString(KEY_EQUIPPED_PREFIX + e.getKey(), e.getValue());
        }
    }

    @NonNull
    private static PetState readFromPrefs(@NonNull SharedPreferences prefs) {
        if (!prefs.contains(KEY_CURRENT_HP)) {
            PetState fresh = PetState.createDefault();
            SharedPreferences.Editor editor = prefs.edit();
            writeCore(editor, fresh);
            editor.apply();
            return fresh;
        }
        String name = prefs.getString(KEY_PET_NAME, PetState.DEFAULT_PET_NAME);
        int maxHp = prefs.getInt(KEY_MAX_HP, PetState.DEFAULT_MAX_HP);
        int hp = prefs.getInt(KEY_CURRENT_HP, PetState.DEFAULT_START_HP);
        Map<String, String> equipped = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String key = e.getKey();
            if (key.startsWith(KEY_EQUIPPED_PREFIX) && e.getValue() instanceof String) {
                equipped.put(key.substring(KEY_EQUIPPED_PREFIX.length()), (String) e.getValue());
            }
        }
        return new PetState(name != null ? name : PetState.DEFAULT_PET_NAME, hp, maxHp, equipped);
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
    }
}
