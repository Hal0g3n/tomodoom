package com.example.tomodoom.contracts;

import android.graphics.RectF;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.model.PetState;

/**
 * Contract for the <b>Maniengine Agent</b>: renders the virtual pet and its clothing/accessory
 * layers into a page's pet viewport (Customise or Pets).
 *
 * <h3>Ownership &amp; lifecycle</h3>
 * <ul>
 *     <li>One renderer instance is created per {@code MainActivity} via
 *     {@code AppContainer#createAssetRenderer()} and shared by the pages that show the pet
 *     ({@code ui.PetStage}), so only one pet is rendered at a time. Implementations may keep
 *     resources (child views, bitmaps, animators) in fields.</li>
 *     <li>{@code container} is the visible page's {@code FrameLayout} {@code R.id.pet_viewport}.
 *     The renderer owns <em>all</em> of its children. Nothing else adds or removes views in it.</li>
 *     <li><b>The container can change.</b> When another page becomes visible, {@link #render} is
 *     called with that page's viewport. Implementations must <em>move</em> their existing views
 *     there (remove them from the old parent, keep loaded state) rather than rebuild or reload.
 *     Views may stay in a hidden page's viewport until then.</li>
 *     <li>{@link #render} is called for every {@link PetState} emission while a pet page is
 *     visible, then {@link #release} once from {@code MainActivity#onDestroy()} with the last
 *     container used. The instance is discarded afterwards. {@link #pause}/{@link #resume}
 *     bracket the time the current container is visible.</li>
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
     * {@code MainActivity#onDestroy()} (via {@code ui.PetStage}). After this, the instance is never
     * used again.
     *
     * @param container the same viewport previously passed to {@link #render}.
     */
    @MainThread
    void release(@NonNull ViewGroup container);

    /**
     * The viewport stopped being visible (its page was paused: another tab, or the app went to
     * the background). Stop animations and other per-frame work until {@link #resume}. Called
     * from the page's {@code onPause()} via {@code ui.PetStage}, possibly before the first
     * {@link #render}.
     * Optional: the default does nothing.
     */
    @MainThread
    default void pause(@NonNull ViewGroup container) {
    }

    /**
     * The viewport is visible again after {@link #pause}. Called from the page's
     * {@code onResume()} via {@code ui.PetStage}, including the first resume. Optional: the default does
     * nothing.
     */
    @MainThread
    default void resume(@NonNull ViewGroup container) {
    }

    /**
     * @return true if this renderer is placed with {@link #setFrame} (and {@link #setTouchBox}).
     * {@code ui.PetStage} then gives it <b>one fixed container for its whole life</b>: a layer the
     * size of the pager, behind the pages, which never moves or resizes. The pet is drawn only in
     * the frame, which follows the visible page's viewport (and slides and shrinks between the
     * Pets and Customise viewports as you swipe); touches on that viewport are forwarded to it.
     * Optional: the default is false, and the renderer is then moved into each page's viewport
     * as described in the class docs.
     */
    @MainThread
    default boolean supportsFrames() {
        return false;
    }

    /**
     * Draws the pet only inside {@code frame}, with rounded corners, framed as it would be in a
     * viewport of that size; the rest of the container stays transparent. {@code null} goes back to
     * filling the whole container. Called on every pager scroll frame, so it must be cheap.
     * Optional: the default does nothing.
     *
     * @param frame          the rect in {@code container}'s coordinates (px), or null.
     * @param cornerRadiusPx the rect's corner radius (px).
     * @param lift           0-1: how far to raise the pet clear of a health bar floating over the
     *                       bottom of the frame (1 on Pets, 0 on Customise, blended in between).
     */
    @MainThread
    default void setFrame(@NonNull ViewGroup container, @Nullable RectF frame,
                          float cornerRadiusPx, float lift) {
    }

    /**
     * Where one-finger drags turn the pet instead of swiping between pages, in {@code container}'s
     * coordinates (px); null while none should (e.g. mid-swipe). Only for renderers that
     * {@linkplain #supportsFrames support frames}. Optional: the default does nothing.
     */
    @MainThread
    default void setTouchBox(@NonNull ViewGroup container, @Nullable RectF box) {
    }
}
