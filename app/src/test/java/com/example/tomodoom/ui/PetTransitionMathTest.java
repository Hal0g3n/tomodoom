package com.example.tomodoom.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PetTransitionMathTest {

    private static final float EPS = 1e-4f;

    @Test
    public void showsOnlyCustomiseAndPets_coversTheFirstPairAndRestingOnPets() {
        assertTrue(PetTransitionMath.showsOnlyCustomiseAndPets(0, 0f));
        assertTrue(PetTransitionMath.showsOnlyCustomiseAndPets(0, 0.5f));
        assertTrue(PetTransitionMath.showsOnlyCustomiseAndPets(1, 0f));
        assertFalse(PetTransitionMath.showsOnlyCustomiseAndPets(1, 0.01f)); // Chat coming in
        assertFalse(PetTransitionMath.showsOnlyCustomiseAndPets(2, 0f));
    }

    @Test
    public void towardsCustomise_isOneOnCustomiseAndZeroOnPets() {
        assertEquals(1f, PetTransitionMath.towardsCustomise(0, 0f), EPS);
        assertEquals(0.75f, PetTransitionMath.towardsCustomise(0, 0.25f), EPS);
        assertEquals(0f, PetTransitionMath.towardsCustomise(1, 0f), EPS);
    }

    @Test
    public void between_interpolatesEdgesAndRadius() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f, 0f);
        PetTransitionMath.Frame half = PetTransitionMath.between(pets, card, 0.5f);
        assertEquals(8f, half.left, EPS);
        assertEquals(8f, half.top, EPS);
        assertEquals(392f, half.right, EPS);
        assertEquals(568f, half.bottom, EPS);
        assertEquals(7f, half.radius, EPS);
        assertEquals(0.5f, half.lift, EPS); // the lift fades evenly, so the pet never dips
    }

    @Test
    public void frameFor_blendsOnlyBetweenCustomiseAndPets() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f, 0f);
        PetTransitionMath.Frame onPets = PetTransitionMath.frameFor(1, 0f, pets, card);
        assertEquals(800f, onPets.bottom, EPS);
        assertEquals(1f, onPets.lift, EPS);
        assertEquals(16f, PetTransitionMath.frameFor(0, 0f, pets, card).left, EPS);
        assertEquals(8f, PetTransitionMath.frameFor(0, 0.5f, pets, card).left, EPS);
        assertSame(pets, PetTransitionMath.frameFor(1, 0.4f, pets, card)); // Chat coming in
        assertSame(pets, PetTransitionMath.frameFor(2, 0f, pets, card));
        assertSame(pets, PetTransitionMath.frameFor(0, 0.5f, pets, null));
        assertSame(card, PetTransitionMath.frameFor(0, 0f, null, card));
        assertNull(PetTransitionMath.frameFor(1, 0f, null, card));
    }

    @Test
    public void modelBox_linesUpWithTheHealthBarOrLeavesTheBottomToSwipes() {
        PetTransitionMath.Frame area = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame bar = new PetTransitionMath.Frame(16f, 680f, 384f, 784f, 0f, 0f);
        PetTransitionMath.Frame withBar = PetTransitionMath.modelBox(area, 16f, bar);
        assertEquals(16f, withBar.left, EPS);
        assertEquals(16f, withBar.top, EPS);
        assertEquals(384f, withBar.right, EPS);
        assertEquals(784f, withBar.bottom, EPS);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(18f, 18f, 382f, 318f, 14f, 0f);
        PetTransitionMath.Frame noBar = PetTransitionMath.modelBox(card, 16f, null);
        assertEquals(34f, noBar.left, EPS);
        assertEquals(18f, noBar.top, EPS);
        assertEquals(366f, noBar.right, EPS);
        assertEquals(258f, noBar.bottom, EPS); // 20% of the 300px height left for swipes
    }

    @Test
    public void between_clampsAndKeepsTheEndpointsExact() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f, 0f);
        assertEquals(384f, PetTransitionMath.between(pets, card, 1.5f).right, 0f);
        assertEquals(800f, PetTransitionMath.between(pets, card, -1f).bottom, 0f);
        assertFalse(PetTransitionMath.between(pets, card, 0.3f).isEmpty());
        assertTrue(new PetTransitionMath.Frame(5f, 5f, 5f, 9f, 0f, 0f).isEmpty());
    }
}
