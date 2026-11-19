package com.example.tomodoom.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A layer that only draws: it never takes a touch, so the views below it get every event. Holds
 * the pet while {@link PetStage} animates it between pages.
 */
public final class PassThroughFrameLayout extends FrameLayout {

    public PassThroughFrameLayout(@NonNull Context context) {
        super(context);
    }

    public PassThroughFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        return false; // not handled: the touch falls through to the pager below
    }
}
