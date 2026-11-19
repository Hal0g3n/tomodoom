package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChatTextScaleTest {

    @Test
    public void defaultIsAnOfferedStep() {
        assertEquals(ChatTextScale.DEFAULT,
                ChatTextScale.stepAt(ChatTextScale.indexOf(ChatTextScale.DEFAULT)), 0f);
    }

    @Test
    public void stepsAreAscending() {
        for (int i = 1; i < ChatTextScale.stepCount(); i++) {
            assertTrue(ChatTextScale.stepAt(i) > ChatTextScale.stepAt(i - 1));
        }
    }

    @Test
    public void stepAtClampsTheIndex() {
        assertEquals(ChatTextScale.stepAt(0), ChatTextScale.stepAt(-3), 0f);
        int last = ChatTextScale.stepCount() - 1;
        assertEquals(ChatTextScale.stepAt(last), ChatTextScale.stepAt(last + 5), 0f);
    }

    @Test
    public void nearestStepSnapsToTheClosestSize() {
        assertEquals(1.0f, ChatTextScale.nearestStep(1.04f), 0f);
        assertEquals(1.15f, ChatTextScale.nearestStep(1.12f), 0f);
        assertEquals(0.8f, ChatTextScale.nearestStep(0.1f), 0f);
        assertEquals(1.5f, ChatTextScale.nearestStep(9f), 0f);
        assertEquals(ChatTextScale.DEFAULT, ChatTextScale.nearestStep(Float.NaN), 0f);
    }

    @Test
    public void percentRounds() {
        assertEquals(115, ChatTextScale.percent(1.15f));
        assertEquals(80, ChatTextScale.percent(0.8f));
    }
}
