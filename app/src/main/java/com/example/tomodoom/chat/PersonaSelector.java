package com.example.tomodoom.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chooses which agent persona speaks for the pet, from its HP, with hysteresis so the
 * personality doesn't flip back and forth while HP hovers around the middle.
 *
 * <ul>
 *     <li>No persona yet (first run): {@link Persona#HIGH_HP} at HP &ge; {@value #INITIAL_THRESHOLD},
 *     otherwise {@link Persona#LOW_HP}.</li>
 *     <li>While {@link Persona#HIGH_HP}: switch to {@link Persona#LOW_HP} once HP &lt;
 *     {@value #LOW_HP_BELOW}.</li>
 *     <li>While {@link Persona#LOW_HP}: switch to {@link Persona#HIGH_HP} once HP &ge;
 *     {@value #HIGH_HP_AT}.</li>
 * </ul>
 *
 * <p>{@link #select} only proposes a persona; {@link #commit} makes it current (the chat service
 * commits after a reply succeeds). Pure Java and not thread-safe: confine it to one thread.</p>
 */
public final class PersonaSelector {

    /** The two personas. Ids are stable: they are persisted. */
    public enum Persona {
        /** Low HP: the bitter persona (Kafka). */
        LOW_HP("low_hp"),
        /** High HP: the cheerful persona (Charon). */
        HIGH_HP("high_hp");

        @NonNull
        private final String id;

        Persona(@NonNull String id) {
            this.id = id;
        }

        @NonNull
        public String getId() {
            return id;
        }

        /** @return the persona with this id, or {@code null} for an unknown or missing id. */
        @Nullable
        public static Persona fromId(@Nullable String id) {
            for (Persona persona : values()) {
                if (persona.id.equals(id)) {
                    return persona;
                }
            }
            return null;
        }
    }

    /** Split used when no persona has been committed yet. */
    public static final int INITIAL_THRESHOLD = 50;
    /** The high-HP persona hands over below this HP. */
    public static final int LOW_HP_BELOW = 40;
    /** The low-HP persona hands over at or above this HP. */
    public static final int HIGH_HP_AT = 60;

    @Nullable
    private Persona current;

    /** @param current the persona last in use (e.g. restored from storage), or null if none. */
    public PersonaSelector(@Nullable Persona current) {
        this.current = current;
    }

    /** The persona that should answer at {@code hp}. Doesn't change the current persona. */
    @NonNull
    public Persona select(int hp) {
        if (current == null) {
            return hp >= INITIAL_THRESHOLD ? Persona.HIGH_HP : Persona.LOW_HP;
        }
        if (current == Persona.HIGH_HP) {
            return hp < LOW_HP_BELOW ? Persona.LOW_HP : Persona.HIGH_HP;
        }
        return hp >= HIGH_HP_AT ? Persona.HIGH_HP : Persona.LOW_HP;
    }

    /** Makes {@code persona} current. */
    public void commit(@NonNull Persona persona) {
        current = persona;
    }

    /** @return the committed persona, or null before the first commit. */
    @Nullable
    public Persona getCurrent() {
        return current;
    }
}
