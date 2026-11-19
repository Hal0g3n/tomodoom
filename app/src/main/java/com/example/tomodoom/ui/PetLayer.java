package com.example.tomodoom.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The layer that holds the pet for the activity's whole life ({@link PetStage}). At rest it is
 * behind the pager: the pages are transparent over their {@link PetViewport}s (and Chat's
 * backdrop), so the pet shows through them. While the pager moves, the stage raises it above the
 * pages, so the pet travels over them.
 *
 * <p>It never takes touches of its own, even while raised: {@link #dispatchTouchEvent} declines
 * them, so they go on to the pager. The visible viewport forwards its touches with {@link #forward}; while it does,
 * {@link #requestDisallowInterceptTouchEvent} goes to that viewport's parents, so the pet's view can
 * keep a drag from the pager, or hand a flick back to it, as if it were inside the viewport.</p>
 */
public final class PetLayer extends FrameLayout {

    /** The viewport whose gesture is being forwarded, or null. */
    @Nullable
    private PetViewport source;

    public PetLayer(@NonNull Context context) {
        super(context);
    }

    public PetLayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        return false; // only forwarded touches reach the pet
    }

    /** Dispatches {@code event} (already in this layer's coordinates) on behalf of {@code from}. */
    boolean forward(@NonNull MotionEvent event, @NonNull PetViewport from) {
        source = from;
        try {
            return super.dispatchTouchEvent(event);
        } finally {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                source = null;
            }
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        ViewParent pagerSide = source != null ? source.getParent() : null;
        if (pagerSide != null) {
            pagerSide.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }
}
