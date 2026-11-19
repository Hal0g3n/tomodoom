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
     * @return true if this renderer implements {@link #setFrame}, so {@code ui.PetStage} may
     * animate the pet between two viewports by moving it into a full-size transition layer.
     * Optional: the default is false, and the pet then just moves when the new page settles.
     */
    @MainThread
    default boolean supportsFramedTransition() {
        return false;
    }

    /**
     * Draws the pet only inside {@code frame}, with rounded corners, framed as it would be in a
     * viewport of that size; the rest of the container stays transparent. {@code null} goes back to
     * filling the whole container. Called once per animation frame during a page transition, so
     * it must be cheap.
     *
     * <p>{@code container} is the container the pet is in <em>or is about to be moved to</em> by the
     * next {@link #render}: a frame may be sent first, so the pet appears in the right place as
     * soon as it is moved. Optional: the default does nothing.</p>
     *
     * @param frame          the rect in {@code container}'s coordinates (px), or null.
     * @param cornerRadiusPx the rect's corner radius (px).
     */
    @MainThread
    default void setFrame(@NonNull ViewGroup container, @Nullable RectF frame,
                          float cornerRadiusPx) {
    }
}
