package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;

/**
 * Pure Java: which step the onboarding permission screen shows next. Usage Access always comes
 * first; once it is granted the user chooses the apps to track; notifications are asked only once
 * those are done or skipped. Skipping Usage Access skips the app choice too (it does nothing
 * without tracking).
 */
public final class OnboardingPermissionFlow {

    /** The permission step on screen. */
    public enum Step { USAGE_ACCESS, TRACKED_APPS, NOTIFICATIONS, DONE }

    private OnboardingPermissionFlow() {
    }

    /**
     * @param usageGranted          Usage Access is granted
     * @param usageSkipped          the user tapped "Not now" on the usage step
     * @param appsHandled           the user visited the watched-apps picker or skipped it
     * @param notificationsNeeded   notifications still need a runtime grant (Android 13+, not granted)
     * @param notificationsHandled  the user answered the notification prompt or skipped it
     */
    @NonNull
    public static Step current(boolean usageGranted, boolean usageSkipped, boolean appsHandled,
                               boolean notificationsNeeded, boolean notificationsHandled) {
        if (!usageGranted && !usageSkipped) {
            return Step.USAGE_ACCESS;
        }
        if (usageGranted && !appsHandled) {
            return Step.TRACKED_APPS;
        }
        if (notificationsNeeded && !notificationsHandled) {
            return Step.NOTIFICATIONS;
        }
        return Step.DONE;
    }
}
