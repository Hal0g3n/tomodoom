package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Validation for the pet's name, chosen on the onboarding screen.
 *
 * <p>Pure Java, unit-testable on the JVM.</p>
 */
public final class PetNames {

    /**
     * Longest allowed name, in UTF-16 chars (the unit the onboarding text field's counter uses).
     */
    public static final int MAX_LENGTH = 20;

    private PetNames() {
    }

    /**
     * Trims the input and collapses each run of internal whitespace to a single space.
     *
     * @return the cleaned name, or {@code null} if it is empty or longer than {@link #MAX_LENGTH}.
     */
    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = out.length() > 0;
            } else {
                if (pendingSpace) {
                    out.append(' ');
                    pendingSpace = false;
                }
                out.append(c);
            }
        }
        if (out.length() == 0 || out.length() > MAX_LENGTH) {
            return null;
        }
        return out.toString();
    }

    /** @return whether {@link #normalize(String)} accepts {@code raw}. */
    public static boolean isValid(@Nullable String raw) {
        return normalize(raw) != null;
    }

    /** @return {@code name} if it is a valid name, otherwise {@link PetState#DEFAULT_PET_NAME}. */
    @NonNull
    public static String orDefault(@Nullable String name) {
        String normalized = normalize(name);
        return normalized != null ? normalized : PetState.DEFAULT_PET_NAME;
    }
}
