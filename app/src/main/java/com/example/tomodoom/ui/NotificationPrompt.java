package com.example.tomodoom.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * The Android 13+ notification permission, used by the scrolling warnings. Onboarding and the Pets
 * page share one "asked already" flag for the process, so a user who just answered in onboarding
 * isn't asked again straight away on Pets.
 */
public final class NotificationPrompt {

    private static boolean requested;

    private NotificationPrompt() {
    }

    /** @return whether notifications still need a runtime grant (Android 13+, not granted). */
    public static boolean isGrantNeeded(@NonNull Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    /** @return whether to show the system prompt now: a grant is needed and not asked this launch. */
    @MainThread
    public static boolean shouldRequest(@NonNull Context context) {
        return !requested && isGrantNeeded(context);
    }

    /** Records that the prompt was shown (or deliberately skipped) during this launch. */
    @MainThread
    public static void markRequested() {
        requested = true;
    }

    /** Records the prompt as asked and shows it, on Android 13+ only (nothing to ask below). */
    @MainThread
    public static void launch(@NonNull ActivityResultLauncher<String> launcher) {
        requested = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
