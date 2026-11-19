package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PetSpeciesTest {

    @Test
    public void allIdsAreValidAndDefaultComesFirst() {
        assertEquals(PetSpecies.DEFAULT, PetSpecies.ALL.get(0));
        for (String id : PetSpecies.ALL) {
            assertTrue(id, PetSpecies.isValid(id));
            assertEquals(id, PetSpecies.orDefault(id));
        }
    }

    @Test
    public void unknownIdsFallBackToTheDefault() {
        assertFalse(PetSpecies.isValid(null));
        assertFalse(PetSpecies.isValid(""));
        assertFalse(PetSpecies.isValid("dragon"));
        assertEquals(PetSpecies.DEFAULT, PetSpecies.orDefault(null));
        assertEquals(PetSpecies.DEFAULT, PetSpecies.orDefault("dragon"));
    }
}
