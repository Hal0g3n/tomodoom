package com.example.tomodoom.viewmodel;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DebugViewModelTest {

    @Test
    public void hpDeltaTo_movesToTarget() {
        assertEquals(-20, DebugViewModel.hpDeltaTo(50, 30, 100));
        assertEquals(30, DebugViewModel.hpDeltaTo(50, 80, 100));
        assertEquals(0, DebugViewModel.hpDeltaTo(50, 50, 100));
    }

    @Test
    public void hpDeltaTo_clampsTarget() {
        assertEquals(-50, DebugViewModel.hpDeltaTo(50, -10, 100));
        assertEquals(50, DebugViewModel.hpDeltaTo(50, 500, 100));
        assertEquals(10, DebugViewModel.hpDeltaTo(110, 120, 120));
    }
}
