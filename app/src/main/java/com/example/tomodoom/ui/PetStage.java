package com.example.tomodoom.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tomodoom.R;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.di.AppContainer;
import com.example.tomodoom.model.PetState;

/**
 * Owns the activity's single {@link ManiengineAssetRenderer} and lends it to whichever page's pet
 * viewport is visible, so only one pet is rendered at a time.
 *
 * <p>A page {@link #attach}es its viewport in {@code onResume} (the renderer moves its view
 * there, keeping the loaded page) and {@link #detach}es it in {@code onPause} and
 * {@code onDestroyView} (the renderer is paused; its view stays put until another page takes it).
 * {@link #render} from a page that isn't the owner is ignored. Main thread only.</p>
 *
 * <p><b>Page transitions.</b> While the pager scrolls between Customise and Pets, or Pets and Chat,
 * and the renderer {@linkplain ManiengineAssetRenderer#supportsFramedTransition supports it}, the
 * pet is moved into one of the activity's transition layers (the same size as the pager) and drawn
 * into a frame that slides and shrinks from one page's viewport to the other's, following the
 * scroll. Customise ↔ Pets uses the layer over the pager, so the pet can shrink into the opaque
 * preview card; Pets ↔ Chat uses the layer behind it (the pages are transparent there), so the
 * chat bubbles and Pets cards slide over the pet as they do when it sits in a viewport. When the
 * pager goes idle the pet is handed to the settled page's viewport, where it looks the same.</p>
 */
@MainThread
public final class PetStage {

    /** Implemented by the activity hosting the pages (MainActivity). */
    public interface Host {
        @NonNull
        PetStage getPetStage();
    }

    @NonNull
    private final Context appContext;
    @Nullable
    private ManiengineAssetRenderer renderer;
    /** The viewport currently showing the pet, or null while no pet page is resumed. */
    @Nullable
    private ViewGroup owner;
    /** The last container the renderer drew into; its views may still be there. */
    @Nullable
    private ViewGroup lastContainer;
    @Nullable
    private PetState lastState;

    /** Each pet page's viewport while its view exists, by page index. */
    private final SparseArray<ViewGroup> viewports = new SparseArray<>();
    /** Over the pager: Customise ↔ Pets. */
    @Nullable
    private ViewGroup frontLayer;
    /** Behind the pager: Pets ↔ Chat. */
    @Nullable
    private ViewGroup backLayer;
    /** The layer holding the pet while {@link #transitioning}. */
    @Nullable
    private ViewGroup activeLayer;
    private int scrollState = ViewPager2.SCROLL_STATE_IDLE;
    /** True while the pet is in {@link #activeLayer}. */
    private boolean transitioning;
    private final int[] layerLocation = new int[2];
    private final int[] viewportLocation = new int[2];

    public PetStage(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** {@code container} becomes the visible pet viewport: the pet moves there and resumes. */
    public void attach(@NonNull ViewGroup container, @Nullable PetState state) {
        if (renderer == null) {
            renderer = AppContainer.from(appContext).createAssetRenderer();
        }
        if (transitioning) {
            clearTransition(); // the settled page takes the pet; it looks the same there
        }
        if (owner != null && owner != container) {
            renderer.pause(owner);
        }
        owner = container;
        if (state != null) {
            lastState = state;
        }
        if (lastState != null) {
            renderer.render(container, lastState);
            lastContainer = container;
        }
        renderer.resume(container);
    }

    /** Renders {@code state} if {@code container} is the visible pet viewport; otherwise no-op. */
    public void render(@NonNull ViewGroup container, @NonNull PetState state) {
        lastState = state;
        if (renderer == null) {
            return;
        }
        if (transitioning && activeLayer != null) {
            renderer.render(activeLayer, state); // the pet stays in the layer until idle
        } else if (owner == container) {
            renderer.render(container, state);
            lastContainer = container;
        }
    }

    /** {@code container} is no longer visible: pause the pet if it was showing there. */
    public void detach(@NonNull ViewGroup container) {
        if (renderer != null && owner == container) {
            renderer.pause(container);
            owner = null;
        }
    }

    /** Frees the renderer for good. Called from the host activity's {@code onDestroy}. */
    public void release() {
        if (renderer != null && lastContainer != null) {
            renderer.release(lastContainer);
        }
        renderer = null;
        owner = null;
        lastContainer = null;
        lastState = null;
        transitioning = false;
        frontLayer = null;
        backLayer = null;
        activeLayer = null;
        viewports.clear();
    }

    // ---------------------------------------------------------------------------------------
    // Page transition
    // ---------------------------------------------------------------------------------------

    /**
     * The layers that hold the pet during a transition (set by the host): {@code front} over the
     * pager (Customise ↔ Pets), {@code back} behind it (Pets ↔ Chat). Both the pager's size.
     */
    public void setTransitionLayers(@Nullable ViewGroup front, @Nullable ViewGroup back) {
        frontLayer = front;
        backLayer = back;
    }

    /** Page {@code page}'s pet viewport exists (call from {@code onViewCreated}). */
    public void registerViewport(int page, @NonNull ViewGroup viewport) {
        viewports.put(page, viewport);
    }

    /** {@code viewport}'s view is going away (call from {@code onDestroyView}). */
    public void unregisterViewport(@NonNull ViewGroup viewport) {
        int index = viewports.indexOfValue(viewport);
        if (index >= 0) {
            viewports.removeAt(index);
        }
        if (transitioning) {
            endTransition(MainPagerAdapter.PAGE_PETS); // can't frame towards a missing page
        }
    }

    /** Forwarded from {@code ViewPager2.OnPageChangeCallback#onPageScrollStateChanged}. */
    public void onPagerScrollStateChanged(int state, int currentPage) {
        scrollState = state;
        if (state == ViewPager2.SCROLL_STATE_IDLE && transitioning) {
            // Still in the layer: the page didn't change (a cancelled swipe), so no onResume.
            endTransition(currentPage);
        }
    }

    /** Forwarded from {@code ViewPager2.OnPageChangeCallback#onPageScrolled}. */
    public void onPagerScrolled(int position, float offset) {
        if (!PetTransitionMath.showsOnlyPetPages(position, offset)) {
            if (transitioning) {
                endTransition(MainPagerAdapter.PAGE_CHAT); // Stats is coming in: Chat keeps the pet
            }
            return;
        }
        ViewGroup layer = layerFor(PetTransitionMath.layerFor(position, offset));
        if (transitioning && layer != activeLayer) {
            // Crossing Pets from one pair to the other (e.g. a Customise → Chat tab tap): the pet
            // lands on Pets, where both pairs meet, then starts again in the other layer.
            endTransition(MainPagerAdapter.PAGE_PETS);
        }
        ManiengineAssetRenderer r = renderer;
        if (r == null || layer == null) {
            return;
        }
        PetTransitionMath.Frame f = currentFrame(layer, position, offset);
        if (f == null) {
            return; // a viewport isn't laid out yet; try again on the next scroll
        }
        RectF frame = new RectF(f.left, f.top, f.right, f.bottom);
        if (transitioning) {
            r.setFrame(layer, frame, f.radius);
            return;
        }
        boolean starting = scrollState != ViewPager2.SCROLL_STATE_IDLE && offset > 0f
                && r.supportsFramedTransition() && lastState != null && lastContainer != null;
        if (!starting) {
            return;
        }
        // Frame first: the page applies it as soon as it has the layer's size, so it never shows
        // a full-size pet when it grows from the Customise viewport.
        r.setFrame(layer, frame, f.radius);
        r.render(layer, lastState);
        lastContainer = layer;
        r.resume(layer); // the pet may be paused (e.g. coming from Stats)
        activeLayer = layer;
        transitioning = true;
    }

    /** @return the layer for a {@code PetTransitionMath.LAYER_*} choice ({@code KEEP}: the current one). */
    @Nullable
    private ViewGroup layerFor(int choice) {
        if (choice == PetTransitionMath.LAYER_FRONT) {
            return frontLayer;
        }
        if (choice == PetTransitionMath.LAYER_BACK) {
            return backLayer;
        }
        return transitioning ? activeLayer : null; // resting on Pets: nothing to start
    }

    /**
     * Hands the pet from the layer to {@code page}'s viewport (or the resumed page's, if that one
     * is gone), paused unless that page is resumed.
     */
    private void endTransition(int page) {
        clearTransition();
        ViewGroup target = viewports.get(page);
        if (target == null) {
            target = owner;
        }
        if (renderer == null || target == null || lastState == null) {
            return;
        }
        renderer.render(target, lastState);
        lastContainer = target;
        if (target != owner) {
            renderer.pause(target);
        }
    }

    private void clearTransition() {
        transitioning = false;
        if (renderer != null && activeLayer != null) {
            renderer.setFrame(activeLayer, null, 0f);
        }
        activeLayer = null;
    }

    /**
     * @return the pet's rect in {@code layer}'s coordinates, {@code offset} of the way from page
     * {@code position}'s viewport to page {@code position + 1}'s, or null if one isn't laid out.
     */
    @Nullable
    private PetTransitionMath.Frame currentFrame(@NonNull ViewGroup layer, int position,
                                                 float offset) {
        if (layer.getWidth() == 0) {
            return null;
        }
        layer.getLocationInWindow(layerLocation);
        PetTransitionMath.Frame from = viewportFrame(viewports.get(position));
        if (from == null) {
            return null;
        }
        if (offset == 0f) {
            return from.isEmpty() ? null : from;
        }
        PetTransitionMath.Frame to = viewportFrame(viewports.get(position + 1));
        if (to == null) {
            return null;
        }
        PetTransitionMath.Frame f = PetTransitionMath.between(from, to, offset);
        return f.isEmpty() ? null : f;
    }

    /** The area the pet fills in {@code viewport} (inside its border), relative to the layer. */
    @Nullable
    private PetTransitionMath.Frame viewportFrame(@Nullable ViewGroup viewport) {
        if (viewport == null || !viewport.isAttachedToWindow() || viewport.getWidth() == 0) {
            return null;
        }
        viewport.getLocationInWindow(viewportLocation);
        float left = viewportLocation[0] - layerLocation[0];
        float top = viewportLocation[1] - layerLocation[1];
        float inset = 0f;
        float radius = 0f;
        if (viewport.getBackground() != null) { // bordered: the renderer insets the pet by the stroke
            Resources res = viewport.getResources();
            inset = res.getDimension(R.dimen.pet_viewport_stroke);
            radius = Math.max(0f, res.getDimension(R.dimen.pet_viewport_corner_radius) - inset);
        }
        return new PetTransitionMath.Frame(left + inset, top + inset,
                left + viewport.getWidth() - inset, top + viewport.getHeight() - inset, radius);
    }
}
