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

    private static PetTransitionMath.Frame[] areas(PetTransitionMath.Frame... byPage) {
        return byPage;
    }

    @Test
    public void frameFor_blendsBetweenNeighbouringPages() {
        PetTransitionMath.Frame card = new PetTransitionMath.Frame(16f, 16f, 384f, 336f, 14f, 0f);
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame[] all = areas(null, card, pets, null, null);
        int customise = MainPagerAdapter.PAGE_CUSTOMISE;
        assertSame(card, PetTransitionMath.frameFor(customise, 0f, all, null));
        PetTransitionMath.Frame half = PetTransitionMath.frameFor(customise, 0.5f, all, null);
        assertEquals(8f, half.left, EPS);
        assertEquals(0.5f, half.lift, EPS);
        assertSame(pets, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_PETS, 0f, all, null));
    }

    @Test
    public void frameFor_blendsIntoABackdropWhileBothPagesSlide() {
        // Live areas: at offset t the Pets page is at -tW and Chat at (1-t)W.
        float w = 400f;
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(-0.5f * w, 0f, 0.5f * w,
                800f, 0f, 1f);
        PetTransitionMath.Frame chat = new PetTransitionMath.Frame(0.5f * w, 0f, 1.5f * w, 700f,
                0f, 0f, PetTransitionMath.BACKDROP_PET_ALPHA);
        PetTransitionMath.Frame half = PetTransitionMath.frameFor(MainPagerAdapter.PAGE_PETS,
                0.5f, areas(null, null, pets, chat, null), null);
        assertEquals(0f, half.left, EPS); // the pet stays put while both pages slide
        assertEquals(w, half.right, EPS);
        assertEquals(750f, half.bottom, EPS);
        assertEquals(0.5f, half.lift, EPS);
        assertEquals(0.6f, half.alpha, EPS);
    }

    @Test
    public void frameFor_coversTheOuterPages() {
        PetTransitionMath.Frame settings = new PetTransitionMath.Frame(0f, 0f, 400f, 700f, 0f, 0f,
                0.2f);
        PetTransitionMath.Frame stats = new PetTransitionMath.Frame(0f, 0f, 400f, 700f, 0f, 0f,
                0.2f);
        PetTransitionMath.Frame[] all = areas(settings, null, null, null, stats);
        assertSame(settings, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_SETTINGS, 0f, all,
                null));
        assertSame(stats, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_STATS, 0f, all,
                null)); // the last page has no right neighbour
    }

    @Test
    public void frameFor_usesTheLaidOutSideOrKeepsTheLastFrame() {
        PetTransitionMath.Frame pets = new PetTransitionMath.Frame(0f, 0f, 400f, 800f, 0f, 1f);
        PetTransitionMath.Frame last = new PetTransitionMath.Frame(0f, 40f, 300f, 600f, 0f, 1f);
        int chat = MainPagerAdapter.PAGE_CHAT;
        assertSame(pets, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_PETS, 0.4f,
                areas(null, null, pets, null, null), last));
        assertSame(pets, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_CUSTOMISE, 0.4f,
                areas(null, null, pets, null, null), last));
        assertSame(last, PetTransitionMath.frameFor(chat, 0.4f,
                areas(null, null, pets, null, null), last)); // mid-jump: stay put
        assertNull(PetTransitionMath.frameFor(chat, 0f, areas(null, null, null, null, null),
                null));
    }

    @Test
    public void frameFor_fadesOutTowardsAHiddenPage() {
        PetTransitionMath.Frame chat = new PetTransitionMath.Frame(0f, 0f, 400f, 700f, 0f, 0f,
                PetTransitionMath.BACKDROP_PET_ALPHA);
        PetTransitionMath.Frame stats = new PetTransitionMath.Frame(400f, 0f, 800f, 700f, 0f, 0f,
                PetTransitionMath.HIDDEN_PET_ALPHA);
        PetTransitionMath.Frame[] all = areas(null, null, null, chat, stats);
        assertEquals(0.1f, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_CHAT, 0.5f, all,
                null).alpha, EPS);
        assertEquals(0f, PetTransitionMath.frameFor(MainPagerAdapter.PAGE_STATS, 0f, all,
                null).alpha, 0f);
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
