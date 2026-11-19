package com.example.tomodoom.chat;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the hidden grading marker that the app asks the Kafka agent to append to replies that
 * grade a quiz answer ({@code [[CORRECT]]} or {@code [[WRONG]]}), and strips every marker from the
 * text the user sees. Pure Java, so it is unit-tested on the JVM.
 */
public final class QuizMarkerParser {

    /** Marker for an accepted answer. */
    public static final String MARKER_CORRECT = "[[CORRECT]]";
    /** Marker for a rejected answer. */
    public static final String MARKER_WRONG = "[[WRONG]]";

    /** Shown if a reply consisted of nothing but a marker. */
    static final String EMPTY_REPLY_FALLBACK = "…";

    /** Tolerates case, inner whitespace and single/double/triple brackets: {@code [ correct ]]}. */
    private static final Pattern MARKER = Pattern.compile(
            "\\[{1,3}\\s*(CORRECT|WRONG)\\s*\\]{1,3}", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** Whether a reply graded the user's answer, and how. */
    public enum Verdict { NONE, CORRECT, WRONG }

    /** The cleaned reply plus the verdict found in it. */
    public static final class Result {
        @NonNull
        private final String text;
        @NonNull
        private final Verdict verdict;

        Result(@NonNull String text, @NonNull Verdict verdict) {
            this.text = text;
            this.verdict = verdict;
        }

        /** The reply with all markers removed, trimmed; never empty. */
        @NonNull
        public String getText() {
            return text;
        }

        @NonNull
        public Verdict getVerdict() {
            return verdict;
        }
    }

    private QuizMarkerParser() {
    }

    /**
     * Parses a raw model reply. If several markers appear, the <b>last</b> one wins (the model's
     * final word on the answer).
     */
    @NonNull
    public static Result parse(@NonNull String reply) {
        Verdict verdict = Verdict.NONE;
        Matcher matcher = MARKER.matcher(reply);
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            verdict = "CORRECT".equalsIgnoreCase(matcher.group(1)) ? Verdict.CORRECT : Verdict.WRONG;
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);
        String text = EXTRA_BLANK_LINES.matcher(cleaned.toString().trim()).replaceAll("\n\n");
        if (text.isEmpty()) {
            text = EMPTY_REPLY_FALLBACK;
        }
        return new Result(text, verdict);
    }
}
