package com.example.tomodoom.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.tomodoom.chat.PersonaSelector.Persona;

import org.junit.Test;

public class PersonaPromptsTest {

    @Test
    public void noNote_withoutAChange() {
        assertNull(PersonaPrompts.moodShiftNote(null, Persona.LOW_HP, 30, 100));
        assertNull(PersonaPrompts.moodShiftNote(Persona.HIGH_HP, Persona.HIGH_HP, 80, 100));
    }

    @Test
    public void dropToLowHp_soursTheMood() {
        String note = PersonaPrompts.moodShiftNote(Persona.HIGH_HP, Persona.LOW_HP, 38, 100);
        assertNotNull(note);
        assertTrue(note.contains("soured"));
        assertTrue(note.contains("38/100 HP"));
        assertTrue(note.contains("don't greet"));
        assertFalse(note.contains("brightened"));
    }

    @Test
    public void riseToHighHp_brightensTheMood() {
        String note = PersonaPrompts.moodShiftNote(Persona.LOW_HP, Persona.HIGH_HP, 60, 100);
        assertNotNull(note);
        assertTrue(note.contains("brightened"));
        assertTrue(note.contains("60/100 HP"));
        assertFalse(note.contains("soured"));
    }
}
