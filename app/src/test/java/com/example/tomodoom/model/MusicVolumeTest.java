package com.example.tomodoom.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MusicVolumeTest {

    @Test
    public void clamp_keepsStepsAndBounds() {
        assertEquals(0, MusicVolume.clamp(-20));
        assertEquals(0, MusicVolume.clamp(0));
        assertEquals(50, MusicVolume.clamp(50));
        assertEquals(100, MusicVolume.clamp(100));
        assertEquals(100, MusicVolume.clamp(250));
    }

    @Test
    public void clamp_snapsToNearestStep() {
        assertEquals(30, MusicVolume.clamp(26));
        assertEquals(20, MusicVolume.clamp(24));
    }

    @Test
    public void defaultIsAStep() {
        assertEquals(MusicVolume.DEFAULT, MusicVolume.clamp(MusicVolume.DEFAULT));
    }

    @Test
    public void gain_isSquaredFraction() {
        assertEquals(0f, MusicVolume.gainFor(0), 0f);
        assertEquals(0.25f, MusicVolume.gainFor(50), 1e-6f);
        assertEquals(1f, MusicVolume.gainFor(100), 0f);
        assertEquals(1f, MusicVolume.gainFor(500), 0f);
    }
}
