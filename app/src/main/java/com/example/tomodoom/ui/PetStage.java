package com.example.tomodoom.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tomodoom.R;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.di.AppContainer;
import com.example.tomodoom.model.PetState;

/**
 * Owns the activity's single {@link ManiengineAssetRenderer} and shows it for whichever pet page
 * (Pets or Customise) is visible, so only one pet is rendered at a time. A page {@link #attach}es
 * its viewport in {@code onResume} and {@link #detach}es it in {@code onPause}/{@code onDestroyView};
 * {@link #render} from a page that isn't the owner is ignored. Main thread only.
 *
 * <p><b>Framed renderers</b> ({@link ManiengineAssetRenderer#supportsFrames()}, the usual case)
 * live in the {@link PetLayer} behind the pager for good: they are never moved or resized, which
 * is what keeps the pet from jumping. The stage tells them where to draw instead: the frame
 * follows the Pets viewport, and while only Customise and Pets are on screen it slides and shrinks
 * from one viewport's pet area to the other's with the pager's scroll offset. Touches on the owner
 * viewport are forwarded to the layer while the pager is idle, and the model box (where drags turn
 * the pet) is worked out here.</p>
 *
 * <p>Other renderers are moved into the owner viewport, as the contract's class docs describe.</p>
 */
@MainThread
public final class PetStage implements PetViewport.TouchForwarder {

    /** Implemented by the activity hosting the pages (MainActivity). */
    public interface Host {
        @NonNull
        PetStage getPetStage();
    }

    @NonNull
    private final Context appContext;
    @Nullable
    private ManiengineAssetRenderer renderer;
    /** True if {@link #renderer} lives in {@link #layer} and is placed with frames. */
    private boolean framed;
    /** The viewport currently showing the pet, or null while no pet page is resumed. */
    @Nullable
    private ViewGroup owner;
    /** The last container the renderer drew into; its views may still be there. */
    @Nullable
    private ViewGroup lastContainer;
    @Nullable
    private PetState lastState;

    @Nullable
    private PetLayer layer;
    /** Each pet page's viewport while its view exists, by page index. */
    private final SparseArray<PetViewport> viewports = new SparseArray<>();
    private int scrollState = ViewPager2.SCROLL_STATE_IDLE;
    private int scrollPosition = MainPagerAdapter.PAGE_PETS;
    private float scrollOffset;
    /** The viewport whose touch gesture is being forwarded to the layer, or null. */
    @Nullable
    private PetViewport forwarding;
    private boolean placementPosted;
    private final int[] layerLocation = new int[2];
    private final int[] viewLocation = new int[2];
    private final View.OnLayoutChangeListener relayout =
            (v, l, t, r, b, oldL, oldT, oldR, oldB) -> postPlacement();

    public PetStage(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** The layer behind the pager that holds a framed renderer (set by the host before pages). */
    public void setPetLayer(@Nullable PetLayer petLayer) {
        if (layer != null) {
            layer.removeOnLayoutChangeListener(relayout);
        }
        layer = petLayer;
        if (layer != null) {
            layer.addOnLayoutChangeListener(relayout);
        }
    }

    /** {@code container} becomes the visible pet viewport: the pet shows there and resumes. */
    public void attach(@NonNull ViewGroup container, @Nullable PetState state) {
        ManiengineAssetRenderer r = ensureRenderer();
        if (state != null) {
            lastState = state;
        }
        if (framed && layer != null) {
            owner = container;
            if (lastState != null) {
                r.render(layer, lastState); // first time: the page loads; later: a cheap update
                lastContainer = layer;
            }
            updatePlacement();
            r.resume(layer);
            return;
        }
        if (owner != null && owner != container) {
            r.pause(owner);
        }
        owner = container;
        if (lastState != null) {
            r.render(container, lastState);
            lastContainer = container;
        }
        r.resume(container);
    }

    /** Renders {@code state} if {@code container} is the visible pet viewport; otherwise no-op. */
    public void render(@NonNull ViewGroup container, @NonNull PetState state) {
        lastState = state;
        if (renderer == null || owner != container) {
            return;
        }
        ViewGroup target = framed && layer != null ? layer : container;
        renderer.render(target, state);
        lastContainer = target;
    }

    /** {@code container} is no longer visible: pause the pet if it was showing there. */
    public void detach(@NonNull ViewGroup container) {
        if (renderer != null && owner == container) {
            renderer.pause(framed && layer != null ? layer : container);
            owner = null;
            updateTouchBox();
        }
    }

    /** Frees the renderer for good. Called from the host activity's {@code onDestroy}. */
    public void release() {
        if (renderer != null && lastContainer != null) {
            renderer.release(lastContainer);
        }
        for (int i = 0; i < viewports.size(); i++) {
            viewports.valueAt(i).setTouchForwarder(null);
            viewports.valueAt(i).removeOnLayoutChangeListener(relayout);
        }
        viewports.clear();
        setPetLayer(null);
        renderer = null;
        framed = false;
        owner = null;
        lastContainer = null;
        lastState = null;
        forwarding = null;
    }

    // ---------------------------------------------------------------------------------------
    // Viewports and the pager
    // ---------------------------------------------------------------------------------------

    /** Page {@code page}'s pet viewport exists (call from {@code onViewCreated}). */
    public void registerViewport(int page, @NonNull PetViewport viewport) {
        PetViewport old = viewports.get(page);
        if (old != null && old != viewport) {
            unregisterViewport(old);
        }
        viewports.put(page, viewport);
        viewport.setTouchForwarder(this);
        viewport.addOnLayoutChangeListener(relayout);
        postPlacement();
    }

    /** {@code viewport}'s view is going away (call from {@code onDestroyView}). */
    public void unregisterViewport(@NonNull PetViewport viewport) {
        int index = viewports.indexOfValue(viewport);
        if (index >= 0) {
            viewports.removeAt(index);
        }
        viewport.setTouchForwarder(null);
        viewport.removeOnLayoutChangeListener(relayout);
        if (forwarding == viewport) {
            forwarding = null;
        }
    }

    /** Forwarded from {@code ViewPager2.OnPageChangeCallback#onPageScrollStateChanged}. */
    public void onPagerScrollStateChanged(int state) {
        scrollState = state;
        updateTouchBox(); // drags only turn the pet while the pager is idle
    }

    /** Forwarded from {@code ViewPager2.OnPageChangeCallback#onPageScrolled}. */
    public void onPagerScrolled(int position, float offset) {
        scrollPosition = position;
        scrollOffset = offset;
        updateFrame();
    }

    @Override
    public boolean forwardTouch(@NonNull PetViewport viewport, @NonNull MotionEvent event) {
        PetLayer target = layer;
        if (!framed || target == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            forwarding = owner == viewport && scrollState == ViewPager2.SCROLL_STATE_IDLE
                    ? viewport : null;
        }
        if (forwarding != viewport) {
            return false;
        }
        viewport.getLocationInWindow(viewLocation);
        target.getLocationInWindow(layerLocation);
        MotionEvent copy = MotionEvent.obtain(event);
        copy.offsetLocation(viewLocation[0] - layerLocation[0], viewLocation[1] - layerLocation[1]);
        target.forward(copy, viewport);
        copy.recycle();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            forwarding = null;
        }
        return true; // keep the gesture, as the pet's view did when it sat in the viewport
    }

    // ---------------------------------------------------------------------------------------
    // Placement
    // ---------------------------------------------------------------------------------------

    @NonNull
    private ManiengineAssetRenderer ensureRenderer() {
        if (renderer == null) {
            renderer = AppContainer.from(appContext).createAssetRenderer();
            framed = renderer.supportsFrames() && layer != null;
        }
        return renderer;
    }

    /** Re-places the pet after a layout pass, once views have their new positions. */
    private void postPlacement() {
        if (layer == null || placementPosted) {
            return;
        }
        placementPosted = true;
        layer.post(() -> {
            placementPosted = false;
            updatePlacement();
        });
    }

    private void updatePlacement() {
        updateFrame();
        updateTouchBox();
    }

    private void updateFrame() {
        PetLayer target = layer;
        if (!framed || renderer == null || target == null || target.getWidth() == 0) {
            return;
        }
        target.getLocationInWindow(layerLocation);
        PetTransitionMath.Frame f = PetTransitionMath.frameFor(scrollPosition, scrollOffset,
                petArea(MainPagerAdapter.PAGE_PETS), petArea(MainPagerAdapter.PAGE_CUSTOMISE));
        if (f == null || f.isEmpty()) {
            return; // not laid out yet; the layout listener tries again
        }
        renderer.setFrame(target, new RectF(f.left, f.top, f.right, f.bottom), f.radius, f.lift);
    }

    private void updateTouchBox() {
        PetLayer target = layer;
        if (!framed || renderer == null || target == null) {
            return;
        }
        RectF box = null;
        if (owner instanceof PetViewport && scrollState == ViewPager2.SCROLL_STATE_IDLE) {
            target.getLocationInWindow(layerLocation);
            PetTransitionMath.Frame area = petArea((PetViewport) owner);
            if (area != null) {
                float inset = owner.getResources().getDimension(R.dimen.pet_model_box_inset);
                PetTransitionMath.Frame b =
                        PetTransitionMath.modelBox(area, inset, healthBarFrame(owner));
                box = new RectF(b.left, b.top, b.right, b.bottom);
            }
        }
        renderer.setTouchBox(target, box);
    }

    /** Page {@code page}'s pet area relative to the layer, or null if it isn't laid out. */
    @Nullable
    private PetTransitionMath.Frame petArea(int page) {
        PetViewport viewport = viewports.get(page);
        return viewport == null ? null : petArea(viewport);
    }

    /**
     * The area the pet fills in {@code viewport}, relative to the layer: inside its border if it
     * has one (with the border's inner corner radius), and lifted clear of a health bar if its
     * page has one. Needs {@link #layerLocation} to be current.
     */
    @Nullable
    private PetTransitionMath.Frame petArea(@NonNull PetViewport viewport) {
        if (!viewport.isAttachedToWindow() || viewport.getWidth() == 0) {
            return null;
        }
        viewport.getLocationInWindow(viewLocation);
        float left = viewLocation[0] - layerLocation[0];
        float top = viewLocation[1] - layerLocation[1];
        float inset = 0f;
        float radius = 0f;
        if (viewport.getBackground() != null) { // bordered: the pet sits inside the stroke
            Resources res = viewport.getResources();
            inset = res.getDimension(R.dimen.pet_viewport_stroke);
            radius = Math.max(0f, res.getDimension(R.dimen.pet_viewport_corner_radius) - inset);
        }
        float lift = healthBarFrame(viewport) != null ? 1f : 0f;
        return new PetTransitionMath.Frame(left + inset, top + inset,
                left + viewport.getWidth() - inset, top + viewport.getHeight() - inset, radius,
                lift);
    }

    /** The health bar on {@code viewport}'s page, relative to the layer, or null if none. */
    @Nullable
    private PetTransitionMath.Frame healthBarFrame(@NonNull View viewport) {
        ViewParent page = viewport.getParent();
        View bar = page instanceof ViewGroup ? ((ViewGroup) page).findViewById(R.id.hp_card) : null;
        if (bar == null || bar.getWidth() == 0) {
            return null;
        }
        bar.getLocationInWindow(viewLocation);
        float left = viewLocation[0] - layerLocation[0];
        float top = viewLocation[1] - layerLocation[1];
        return new PetTransitionMath.Frame(left, top, left + bar.getWidth(),
                top + bar.getHeight(), 0f, 0f);
    }
}
