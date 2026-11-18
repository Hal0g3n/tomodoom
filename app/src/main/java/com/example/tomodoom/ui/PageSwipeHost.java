package com.example.tomodoom.ui;

import androidx.annotation.MainThread;

/**
 * Implemented by the activity that hosts the swipeable top-level pages. A page calls it to
 * suspend page swipes while it needs horizontal drags itself (e.g. Pets while its customisation
 * panel is open).
 *
 * <p>A page that disables swiping must re-enable it when it stops being the current page
 * ({@code onPause}), or the user can't swipe back to it.</p>
 */
public interface PageSwipeHost {

    @MainThread
    void setPageSwipeEnabled(boolean enabled);
}
