package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;

/**
 * Pure Java: which permission the onboarding screen asks for next. Usage Access always comes
 * first; notifications are asked only once the usage step is granted or skipped. (Choosing the
 * tracked apps is the next onboarding page, not a step here.)
 */
public final class OnboardingPermissionFlow {

    /** The permission step on screen. */
    public enum Step { USAGE_ACCESS, NOTIFICATIONS, DONE }

    private OnboardingPermissionFlow() {
    }

    /**
     * @param usageGranted          Usage Access is granted
     * @param usageSkipped          the user tapped "Not now" on the usage step
     * @param notificationsNeeded   notifications still need a runtime grant (Android 13+, not granted)
     * @param notificationsHandled  the user answered the notification prompt or skipped it
     */
    @NonNull
    public static Step current(boolean usageGranted, boolean usageSkipped,
                               boolean notificationsNeeded, boolean notificationsHandled) {
        if (!usageGranted && !usageSkipped) {
            return Step.USAGE_ACCESS;
        }
        if (notificationsNeeded && !notificationsHandled) {
            return Step.NOTIFICATIONS;
        }
        return Step.DONE;
    }
}
