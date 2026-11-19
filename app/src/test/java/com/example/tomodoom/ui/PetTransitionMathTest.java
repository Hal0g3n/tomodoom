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
    public void parkedFrame_movesTheLastFrameOffTheLeftEdge() {
        PetTransitionMath.Frame last = new PetTransitionMath.Frame(0f, 40f, 300f, 600f, 0f, 1f);
        PetTransitionMath.Frame parked = PetTransitionMath.parkedFrame(4, null, last);
        assertEquals(-300f, parked.left, EPS);
        assertEquals(0f, parked.right, EPS);
        assertEquals(40f, parked.top, EPS);
        assertEquals(600f, parked.bottom, EPS);
        assertEquals(1f, parked.lift, EPS);
    }

    @Test
    public void isOffScreen_onlyWhenWhollyOutsideTheLayer() {
        assertTrue(PetTransitionMath.isOffScreen(
                new PetTransitionMath.Frame(-300f, 0f, 0f, 600f, 0f, 1f), 1080f));
        assertTrue(PetTransitionMath.isOffScreen(
                new PetTransitionMath.Frame(1080f, 0f, 1380f, 600f, 0f, 1f), 1080f));
        assertFalse(PetTransitionMath.isOffScreen(
                new PetTransitionMath.Frame(-299f, 0f, 1f, 600f, 0f, 1f), 1080f));
    }

    @Test
    public void parkedFrame_onlyWhenPastPetsWithPetsDetached() {
        PetTransitionMath.Frame last = new PetTransitionMath.Frame(0f, 0f, 300f, 600f, 0f, 1f);
        assertNull(PetTransitionMath.parkedFrame(4, last, last)); // Pets laid out
        assertNull(PetTransitionMath.parkedFrame(1, null, last)); // on Pets
        assertNull(PetTransitionMath.parkedFrame(0, null, last)); // on Customise
        assertNull(PetTransitionMath.parkedFrame(3, null, null)); // nothing sent yet
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
    public void towardsChat_followsTheOffsetFromPets() {
        assertEquals(0f, PetTransitionMath.towardsChat(0, 0.5f), EPS);
        assertEquals(0f, PetTransitionMath.towardsChat(1, 0f), EPS);
        assertEquals(0.25f, PetTransitionMath.towardsChat(1, 0.25f), EPS);
        assertEquals(1f, PetTransitionMath.towardsChat(2, 0f), EPS);
        assertEquals(1f, PetTransitionMath.towardsChat(3, 0f), EPS);
    }

    @Test
    public void frame_isOpaqueByDefaultAndAlphaIsInterpolated() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame chat = new PetTransitionMath.Frame(0f, 0f, 400f, 700f, 0f, 0f,
                0.2f);
        assertEquals(1f, pets.alpha, 0f);
        assertEquals(0.6f, PetTransitionMath.between(pets, chat, 0.5f).alpha, EPS);
    }

    @Test
    public void frameFor_blendsBetweenPetsAndTheChatBackdrop() {
        // Live areas: at offset t the Pets page is at -tW and Chat at (1-t)W.
        float w = 400f;
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(-0.5f * w, 0f, 0.5f * w,
                800f, 0f, 1f);
        PetTransitionMath.Frame chat = new PetTransitionMath.Frame(0.5f * w, 0f, 1.5f * w, 700f,
                0f, 0f, PetTransitionMath.CHAT_PET_ALPHA);
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f, 0f);
        PetTransitionMath.Frame half = PetTransitionMath.frameFor(1, 0.5f, pets, card, chat);
        assertEquals(0f, half.left, EPS); // the pet stays put while both pages slide
        assertEquals(w, half.right, EPS);
        assertEquals(750f, half.bottom, EPS);
        assertEquals(0.5f, half.lift, EPS);
        assertEquals(0.6f, half.alpha, EPS);
        assertSame(chat, PetTransitionMath.frameFor(2, 0f, pets, card, chat));
        assertSame(chat, PetTransitionMath.frameFor(2, 0.3f, pets, card, chat)); // to Stats
        assertSame(chat, PetTransitionMath.frameFor(3, 0f, null, card, chat));
        assertEquals(1f, PetTransitionMath.frameFor(1, 0f, pets, card, chat).alpha, EPS);
        assertSame(pets, PetTransitionMath.frameFor(1, 0.5f, pets, card, null));
        assertNull(PetTransitionMath.frameFor(1, 0.5f, null, card, chat));
        assertEquals(8f, PetTransitionMath.frameFor(0, 0.5f, pets, card, chat).top, EPS);
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
