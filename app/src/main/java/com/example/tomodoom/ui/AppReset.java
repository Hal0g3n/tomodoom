package com.example.tomodoom.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The hidden full reset (long-press the Wardrobe tab, or the debug panel's Reset button).
 *
 * <p>{@link #wipe} calls {@link ActivityManager#clearApplicationUserData()}, which erases all app
 * data (pet state, onboarding flag, WebView storage) and kills the process, so the next launch is a
 * fresh install. Bypassing {@code PetStateRepository} is fine because no observer outlives the
 * process (CLAUDE.md §3.1).</p>
 */
public final class AppReset {

    private static final String TAG = "AppReset";

    private AppReset() {
    }

    /** Asks before wiping everything; Cancel changes nothing. */
    @MainThread
    public static void confirm(@NonNull Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(R.string.reset_confirm, (dialog, which) -> wipe(activity))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Wipes all app data and kills the process; if that fails, just closes the app. */
    @MainThread
    public static void wipe(@NonNull Activity activity) {
        Log.i(TAG, "hidden reset: clearing app data");
        ActivityManager am = activity.getSystemService(ActivityManager.class);
        if (am == null || !am.clearApplicationUserData()) {
            Log.w(TAG, "clearApplicationUserData failed; closing without a reset");
            activity.finishAffinity();
        }
    }
}
