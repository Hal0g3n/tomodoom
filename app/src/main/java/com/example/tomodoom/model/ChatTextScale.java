package com.example.tomodoom.model;

/**
 * The chatbot text sizes the Settings page offers, as multiples of the default bubble size
 * ({@code @dimen/chat_bubble_text_size}). Pure Java, unit-testable on the JVM.
 */
public final class ChatTextScale {

    /** The default size: the bubbles' own text size. */
    public static final float DEFAULT = 1.0f;

    /** Every size offered, smallest first. The Settings slider has one stop per entry. */
    private static final float[] STEPS = {0.8f, 0.9f, 1.0f, 1.15f, 1.3f, 1.5f};

    private ChatTextScale() {
    }

    /** @return how many sizes there are. */
    public static int stepCount() {
        return STEPS.length;
    }

    /**
     * @return the scale at {@code index}, clamped to the valid range.
     */
    public static float stepAt(int index) {
        if (index < 0) {
            index = 0;
        } else if (index >= STEPS.length) {
            index = STEPS.length - 1;
        }
        return STEPS[index];
    }

    /** @return the index of the offered size closest to {@code scale} (NaN → the default). */
    public static int indexOf(float scale) {
        if (Float.isNaN(scale)) {
            scale = DEFAULT;
        }
        int best = 0;
        for (int i = 1; i < STEPS.length; i++) {
            if (Math.abs(STEPS[i] - scale) < Math.abs(STEPS[best] - scale)) {
                best = i;
            }
        }
        return best;
    }

    /** @return the offered size closest to {@code scale}; a stored value is snapped with this. */
    public static float nearestStep(float scale) {
        return STEPS[indexOf(scale)];
    }

    /** @return {@code scale} as a whole percentage, e.g. 1.15 → 115. */
    public static int percent(float scale) {
        return Math.round(scale * 100f);
    }
}
