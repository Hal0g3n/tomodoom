package com.example.tomodoom.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.chat.PersonaSelector.Persona;

/**
 * Hidden prompt text that makes a persona switch feel like one pet whose mood changed, rather
 * than a different bot taking over. Pure Java.
 */
public final class PersonaPrompts {

    private PersonaPrompts() {
    }

    /**
     * The one-turn note added to the system prompt on the first reply after the persona changes.
     *
     * @return the note, or {@code null} when there is no change ({@code from} is null or equal
     * to {@code to}).
     */
    @Nullable
    public static String moodShiftNote(@Nullable Persona from, @NonNull Persona to,
                                       int hp, int maxHp) {
        if (from == null || from == to) {
            return null;
        }
        boolean soured = to == Persona.LOW_HP;
        return "\n\n# MOOD SHIFT (hidden from the user, this reply only)\n"
                + "Since your last reply, "
                + (soured ? "the user's doom-scrolling has drained your health"
                        : "the user's good habits have restored your health")
                + " to " + hp + "/" + maxHp + " HP, and your mood has "
                + (soured ? "soured" : "brightened") + ". "
                + "You are the same pet with the same memory of this conversation: don't greet "
                + "the user again or restart. Open this reply with one short in-character line "
                + "that shows the change of mood, then carry on with the current topic and "
                + "question.";
    }
}
