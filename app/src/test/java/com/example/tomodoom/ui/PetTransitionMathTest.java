package com.example.tomodoom.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PetTransitionMathTest {

    private static final float EPS = 1e-4f;

    @Test
    public void showsOnlyPetPages_coversCustomisePetsAndChat() {
        assertTrue(PetTransitionMath.showsOnlyPetPages(0, 0f));
        assertTrue(PetTransitionMath.showsOnlyPetPages(0, 0.5f));
        assertTrue(PetTransitionMath.showsOnlyPetPages(1, 0f));
        assertTrue(PetTransitionMath.showsOnlyPetPages(1, 0.5f)); // Pets <-> Chat
        assertTrue(PetTransitionMath.showsOnlyPetPages(2, 0f)); // resting on Chat
        assertFalse(PetTransitionMath.showsOnlyPetPages(2, 0.01f)); // Stats coming in
        assertFalse(PetTransitionMath.showsOnlyPetPages(3, 0f));
    }

    @Test
    public void layerFor_frontForCustomiseBackForChatKeepOnPets() {
        assertEquals(PetTransitionMath.LAYER_FRONT, PetTransitionMath.layerFor(0, 0f));
        assertEquals(PetTransitionMath.LAYER_FRONT, PetTransitionMath.layerFor(0, 0.9f));
        assertEquals(PetTransitionMath.LAYER_KEEP, PetTransitionMath.layerFor(1, 0f));
        assertEquals(PetTransitionMath.LAYER_BACK, PetTransitionMath.layerFor(1, 0.1f));
        assertEquals(PetTransitionMath.LAYER_BACK, PetTransitionMath.layerFor(2, 0f));
    }

    @Test
    public void between_interpolatesEdgesAndRadius() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f);
        PetTransitionMath.Frame half = PetTransitionMath.between(pets, card, 0.5f);
        assertEquals(8f, half.left, EPS);
        assertEquals(8f, half.top, EPS);
        assertEquals(392f, half.right, EPS);
        assertEquals(568f, half.bottom, EPS);
        assertEquals(7f, half.radius, EPS);
    }

    @Test
    public void between_clampsAndKeepsTheEndpointsExact() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f);
        assertEquals(384f, PetTransitionMath.between(pets, card, 1.5f).right, 0f);
        assertEquals(800f, PetTransitionMath.between(pets, card, -1f).bottom, 0f);
        assertFalse(PetTransitionMath.between(pets, card, 0.3f).isEmpty());
        assertTrue(new PetTransitionMath.Frame(5f, 5f, 5f, 9f, 0f).isEmpty());
    }
}
