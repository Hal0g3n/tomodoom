package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PetNamesTest {

    @Test
    public void normalize_trimsAndCollapsesWhitespace() {
        assertEquals("Mochi", PetNames.normalize("  Mochi \n"));
        assertEquals("Sir Fluff", PetNames.normalize("Sir \t  Fluff"));
        assertEquals("Ünï 🐾", PetNames.normalize("Ünï 🐾"));
    }

    @Test
    public void normalize_rejectsEmptyAndBlank() {
        assertNull(PetNames.normalize(null));
        assertNull(PetNames.normalize(""));
        assertNull(PetNames.normalize("   \t\n"));
        assertFalse(PetNames.isValid(" "));
    }

    @Test
    public void normalize_enforcesMaxLengthAfterCleaning() {
        String max = "abcdefghijklmnopqrst";
        assertEquals(PetNames.MAX_LENGTH, max.length());
        assertEquals(max, PetNames.normalize(max));
        assertNull(PetNames.normalize(max + "u"));
        // Surrounding whitespace does not count towards the limit.
        assertTrue(PetNames.isValid("   " + max + "   "));
    }

    @Test
    public void orDefault_fallsBackToDefaultName() {
        assertEquals(PetState.DEFAULT_PET_NAME, PetNames.orDefault(null));
        assertEquals(PetState.DEFAULT_PET_NAME, PetNames.orDefault("  "));
        assertEquals("Rex", PetNames.orDefault(" Rex "));
    }
}
