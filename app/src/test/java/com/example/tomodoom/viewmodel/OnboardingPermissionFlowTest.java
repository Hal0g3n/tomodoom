package com.example.tomodoom.viewmodel;

import static org.junit.Assert.assertEquals;

import com.example.tomodoom.viewmodel.OnboardingPermissionFlow.Step;

import org.junit.Test;

public class OnboardingPermissionFlowTest {

    @Test
    public void usageAccessComesFirst() {
        assertEquals(Step.USAGE_ACCESS,
                OnboardingPermissionFlow.current(false, false, false, true, false));
        assertEquals(Step.USAGE_ACCESS,
                OnboardingPermissionFlow.current(false, false, false, false, false));
    }

    @Test
    public void trackedAppsFollowGrantedUsage() {
        assertEquals(Step.TRACKED_APPS,
                OnboardingPermissionFlow.current(true, false, false, true, false));
        assertEquals(Step.TRACKED_APPS,
                OnboardingPermissionFlow.current(true, false, false, false, false));
    }

    @Test
    public void skippedUsageSkipsTrackedApps() {
        assertEquals(Step.NOTIFICATIONS,
                OnboardingPermissionFlow.current(false, true, false, true, false));
        assertEquals(Step.DONE,
                OnboardingPermissionFlow.current(false, true, false, false, false));
    }

    @Test
    public void notificationsFollowHandledApps() {
        assertEquals(Step.NOTIFICATIONS,
                OnboardingPermissionFlow.current(true, false, true, true, false));
    }

    @Test
    public void doneWhenNothingLeftToAsk() {
        assertEquals(Step.DONE, OnboardingPermissionFlow.current(true, false, true, true, true));
        assertEquals(Step.DONE, OnboardingPermissionFlow.current(true, false, true, false, false));
        assertEquals(Step.DONE, OnboardingPermissionFlow.current(false, true, true, false, false));
    }
}
