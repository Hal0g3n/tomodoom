package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PetMoodTest {

    private static PetMood moodAt(int hp) {
        return PetMood.of(new PetState("Tomo", hp, 100, null));
    }

    @Test
    public void of_usesHpThresholds() {
        assertEquals(PetMood.FAINTED, moodAt(0));
        assertEquals(PetMood.STRUGGLING, moodAt(1));
        assertEquals(PetMood.STRUGGLING, moodAt(39));
        assertEquals(PetMood.OKAY, moodAt(40));
        assertEquals(PetMood.OKAY, moodAt(74));
        assertEquals(PetMood.THRIVING, moodAt(75));
        assertEquals(PetMood.THRIVING, moodAt(100));
    }

    @Test
    public void defaultPet_isOkay() {
        assertEquals(PetMood.OKAY, PetMood.of(PetState.createDefault()));
        assertEquals("okay", PetMood.OKAY.getId());
    }
}
