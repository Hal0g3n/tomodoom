package com.example.tomodoom.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.tomodoom.chat.PersonaSelector.Persona;

import org.junit.Test;

public class PersonaSelectorTest {

    @Test
    public void noPersonaYet_splitsAtFifty() {
        PersonaSelector selector = new PersonaSelector(null);
        assertEquals(Persona.LOW_HP, selector.select(49));
        assertEquals(Persona.HIGH_HP, selector.select(50));
        assertEquals(Persona.HIGH_HP, selector.select(100));
        assertEquals(Persona.LOW_HP, selector.select(0));
    }

    @Test
    public void highPersona_holdsUntilHpDropsBelowForty() {
        PersonaSelector selector = new PersonaSelector(Persona.HIGH_HP);
        assertEquals(Persona.HIGH_HP, selector.select(59));
        assertEquals(Persona.HIGH_HP, selector.select(45));
        assertEquals(Persona.HIGH_HP, selector.select(40));
        assertEquals(Persona.LOW_HP, selector.select(39));
    }

    @Test
    public void lowPersona_holdsUntilHpReachesSixty() {
        PersonaSelector selector = new PersonaSelector(Persona.LOW_HP);
        assertEquals(Persona.LOW_HP, selector.select(40));
        assertEquals(Persona.LOW_HP, selector.select(55));
        assertEquals(Persona.LOW_HP, selector.select(59));
        assertEquals(Persona.HIGH_HP, selector.select(60));
    }

    @Test
    public void selectDoesNotCommit() {
        PersonaSelector selector = new PersonaSelector(null);
        selector.select(10);
        assertNull(selector.getCurrent());
        selector.commit(Persona.LOW_HP);
        assertEquals(Persona.LOW_HP, selector.getCurrent());
        assertEquals(Persona.LOW_HP, selector.select(50)); // inside the band: stays low
    }

    @Test
    public void ids_roundTrip() {
        for (Persona persona : Persona.values()) {
            assertEquals(persona, Persona.fromId(persona.getId()));
        }
        assertNull(Persona.fromId(null));
        assertNull(Persona.fromId("kafka"));
    }
}
