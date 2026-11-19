package com.example.tomodoom.stubs;

import static org.junit.Assert.assertEquals;

import com.example.tomodoom.model.PetState;

import org.junit.Test;

public class DefaultMcpPetActionHandlerTest {

    @Test
    public void quizAwardMessage_namesThePetAndTheReward() {
        PetState after = new PetState("Mochi", 60, 100, null);
        assertEquals("Mochi will remember that (+10 HP)",
                DefaultMcpPetActionHandler.quizAwardMessage(10, after));
    }
}
