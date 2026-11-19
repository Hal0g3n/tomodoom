package com.example.tomodoom.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WatchedAppsTest {

    @Test
    public void defaultsAreTheCommonSocialApps() {
        assertEquals(8, WatchedApps.DEFAULT_PACKAGES.size());
        assertTrue(WatchedApps.DEFAULT_PACKAGES.contains("com.instagram.android"));
        assertTrue(WatchedApps.DEFAULT_PACKAGES.contains("com.google.android.youtube"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void defaultsCannotBeModified() {
        WatchedApps.DEFAULT_PACKAGES.add("com.example.work");
    }

    @Test
    public void withAddsAndRemovesWithoutTouchingTheOriginal() {
        Set<String> original = new HashSet<>(Collections.singleton("a"));

        Set<String> added = WatchedApps.with(original, "b", true);
        Set<String> removed = WatchedApps.with(original, "a", false);

        assertEquals(new HashSet<>(Arrays.asList("a", "b")), added);
        assertTrue(removed.isEmpty());
        assertEquals(Collections.singleton("a"), original);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void withReturnsAnUnmodifiableSet() {
        WatchedApps.with(Collections.<String>emptySet(), "a", true).add("b");
    }

    @Test
    public void sortsWatchedFirstThenByLabelIgnoringCase() {
        InstalledApp notes = new InstalledApp("com.notes", "notes");
        InstalledApp mail = new InstalledApp("com.mail", "Mail");
        InstalledApp video = new InstalledApp("com.video", "Video");
        InstalledApp chat = new InstalledApp("com.chat", "chat");
        List<InstalledApp> apps = Arrays.asList(notes, video, mail, chat);
        Set<String> watched = new HashSet<>(Arrays.asList("com.video", "com.chat"));

        List<InstalledApp> sorted = WatchedApps.sortForDisplay(apps, watched);

        assertEquals(Arrays.asList(chat, video, mail, notes), sorted);
        assertEquals(notes, apps.get(0));
    }

    @Test
    public void sameLabelsAreOrderedByPackage() {
        InstalledApp b = new InstalledApp("com.b", "Same");
        InstalledApp a = new InstalledApp("com.a", "Same");

        assertEquals(Arrays.asList(a, b),
                WatchedApps.sortForDisplay(Arrays.asList(b, a), Collections.<String>emptySet()));
    }

    @Test
    public void countsOnlyListedAppsThatAreWatched() {
        List<InstalledApp> apps = Arrays.asList(
                new InstalledApp("com.a", "A"), new InstalledApp("com.b", "B"));
        Set<String> watched = new HashSet<>(Arrays.asList("com.a", "com.not.installed"));

        assertEquals(1, WatchedApps.countWatched(apps, watched));
        assertFalse(watched.isEmpty());
    }

    @Test
    public void defaultsCountOnlyTheInstalledOnes() {
        List<InstalledApp> installed = Arrays.asList(
                new InstalledApp("com.instagram.android", "Instagram"),
                new InstalledApp("com.google.android.youtube", "YouTube"),
                new InstalledApp("com.android.chrome", "Chrome"));

        assertEquals(2, WatchedApps.countWatched(installed, WatchedApps.DEFAULT_PACKAGES));
    }
}
