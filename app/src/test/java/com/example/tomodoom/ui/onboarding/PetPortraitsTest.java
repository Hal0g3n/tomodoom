package com.example.tomodoom.ui.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.tomodoom.model.PetSpecies;

import org.junit.Test;

public class PetPortraitsTest {

    @Test
    public void everySpeciesHasAPortraitAndName() {
        for (String id : PetSpecies.ALL) {
            assertNotEquals(id, 0, PetPortraits.portraitFor(id));
            assertNotEquals(id, 0, PetPortraits.nameFor(id));
        }
    }

    @Test
    public void unknownSpeciesHasNone() {
        assertEquals(0, PetPortraits.portraitFor("dragon"));
        assertEquals(0, PetPortraits.nameFor("dragon"));
    }
}
