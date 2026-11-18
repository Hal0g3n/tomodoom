package com.example.tomodoom.ui;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    /** The last viewport the renderer drew into; its views may still be there. */
    @Nullable
    private ViewGroup lastContainer;
    @Nullable
    private PetState lastState;

    public PetStage(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** {@code container} becomes the visible pet viewport: the pet moves there and resumes. */
    public void attach(@NonNull ViewGroup container, @Nullable PetState state) {
        if (renderer == null) {
            renderer = AppContainer.from(appContext).createAssetRenderer();
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
        if (renderer != null && owner == container) {
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
    }
}
