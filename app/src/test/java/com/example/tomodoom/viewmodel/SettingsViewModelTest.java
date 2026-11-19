package com.example.tomodoom.viewmodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsViewModelTest {

    @Test
    public void aNewValidNameIsAChange() {
        assertTrue(SettingsViewModel.isNameChange("Mochi", "Tomo"));
    }

    @Test
    public void theSameNameAfterNormalisingIsNoChange() {
        assertFalse(SettingsViewModel.isNameChange("  Tomo ", "Tomo"));
    }

    @Test
    public void whitespaceIsCollapsedBeforeComparing() {
        assertFalse(SettingsViewModel.isNameChange("Big   Tomo", "Big Tomo"));
        assertTrue(SettingsViewModel.isNameChange("Big   Mochi", "Big Tomo"));
    }

    @Test
    public void invalidNamesAreNoChange() {
        assertFalse(SettingsViewModel.isNameChange("", "Tomo"));
        assertFalse(SettingsViewModel.isNameChange("   ", "Tomo"));
        assertFalse(SettingsViewModel.isNameChange(null, "Tomo"));
        assertFalse(SettingsViewModel.isNameChange("abcdefghijklmnopqrstu", "Tomo")); // 21 chars
    }
}
