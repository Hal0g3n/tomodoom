package com.example.tomodoom.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A page's pet viewport ({@code R.id.pet_viewport}). With a frame-capable renderer the pet isn't
 * inside it: it is drawn in {@link PetLayer} behind the pages, lined up with this view, and this
 * view hands its touches to {@link PetStage} to forward there. Otherwise it is a plain
 * {@code FrameLayout} the renderer draws into.
 */
public final class PetViewport extends FrameLayout {

    /** Takes this viewport's touches; returns false to let it handle them itself. */
    interface TouchForwarder {
        boolean forwardTouch(@NonNull PetViewport viewport, @NonNull MotionEvent event);
    }

    @Nullable
    private TouchForwarder forwarder;

    public PetViewport(@NonNull Context context) {
        super(context);
    }

    public PetViewport(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    void setTouchForwarder(@Nullable TouchForwarder forwarder) {
        this.forwarder = forwarder;
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (forwarder != null && forwarder.forwardTouch(this, event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
}
