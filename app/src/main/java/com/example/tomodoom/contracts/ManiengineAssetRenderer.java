package com.example.tomodoom.contracts;

import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.model.PetState;

/**
 * Contract for the <b>Maniengine Agent</b>: renders the virtual pet and its clothing/accessory
 * layers into the Pets page viewport.
 *
 * <h3>Ownership &amp; lifecycle</h3>
 * <ul>
 *     <li>One renderer instance is created per Pets view lifecycle via
 *     {@code AppContainer#createAssetRenderer()}, so implementations may keep per-viewport
 *     resources (child views, bitmaps, animators) in fields.</li>
 *     <li>{@code container} is the {@code FrameLayout} {@code R.id.pet_viewport}. The renderer owns
 *     <em>all</em> of its children. Nothing else adds or removes views in it.</li>
 *     <li>{@link #render} is called for every {@link PetState} emission, then {@link #release} once
 *     from {@code PetsFragment#onDestroyView()}. The instance is discarded afterwards.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * All methods are invoked on the <b>main thread</b> and must return quickly. Asset decoding or
 * loading must happen on an implementation-owned background executor, with results posted back
 * to the main thread before touching views. Background work must be cancelled in
 * {@link #release}.
 *
 * <h3>Asset format</h3>
 * Not decided yet (raster layers, vector drawables, Lottie, skeletal…). Callers make no
 * assumptions; item IDs come from {@link com.example.tomodoom.model.AccessoryCatalog}.
 *
 * <p>Default implementation: {@code stubs.PlaceholderManiengineRenderer}.</p>
 */
public interface ManiengineAssetRenderer {

    /**
     * Renders, or updates in place, the pet for {@code state} inside {@code container}.
     *
     * <p>Must be <b>idempotent and cheap</b>: it is called on HP changes as well as equip changes.
     * Implementations should diff against what is already displayed instead of rebuilding the
     * view hierarchy every time.</p>
     *
     * @param container the pet viewport; never null, already attached to the window.
     * @param state     the latest pet state; never null. Use
     *                  {@link PetState#getEquippedItems()} (slot ID → item ID) for layers and
     *                  {@link PetState#getHpFraction()} / {@link PetState#isFainted()} for mood.
     */
    @MainThread
    void render(@NonNull ViewGroup container, @NonNull PetState state);

    /**
     * Releases everything this renderer added to {@code container}: removes child views,
     * cancels animations and background loads, and frees bitmaps. Called exactly once from
     * {@code PetsFragment#onDestroyView()}. After this, the instance is never used again.
     *
     * @param container the same viewport previously passed to {@link #render}.
     */
    @MainThread
    void release(@NonNull ViewGroup container);
}
