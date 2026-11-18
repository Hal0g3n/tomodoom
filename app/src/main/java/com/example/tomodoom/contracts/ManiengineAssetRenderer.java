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
 *     from {@code PetsFragment#onDestroyView()}. The instance is discarded afterwards.
 *     {@link #pause}/{@link #resume} bracket the time the page is visible.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * All methods are invoked on the <b>main thread</b> and must return quickly. Asset decoding or
 * loading must happen on an implementation-owned background executor, with results posted back
 * to the main thread before touching views. Background work must be cancelled in
 * {@link #release}.
 *
 * <h3>Asset format</h3>
 * Maniengine's output is an <b>HTML page</b> (a 3D scene), shown by
 * {@code maniengine.HtmlManiengineRenderer} in a WebView; the page's state contract is
 * {@code maniengine.ManiengineProtocol}. Callers still make no assumptions about the format: they
 * only pass {@link PetState}. Item IDs come from
 * {@link com.example.tomodoom.model.AccessoryCatalog}.
 *
 * <p>Wired implementation: {@code maniengine.HtmlManiengineRenderer}. Reference/fallback:
 * {@code stubs.PlaceholderManiengineRenderer}.</p>
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

    /**
     * The viewport stopped being visible (the Pets page was paused: another tab, or the app went
     * to the background). Stop animations and other per-frame work until {@link #resume}. Called
     * from {@code PetsFragment#onPause()}, possibly before the first {@link #render}.
     * Optional: the default does nothing.
     */
    @MainThread
    default void pause(@NonNull ViewGroup container) {
    }

    /**
     * The viewport is visible again after {@link #pause}. Called from
     * {@code PetsFragment#onResume()}, including the first resume. Optional: the default does
     * nothing.
     */
    @MainThread
    default void resume(@NonNull ViewGroup container) {
    }
}
