package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A structured quiz question delivered by {@code TruFarChatService.Listener#onQuizPrompt}.
 *
 * <p>While a prompt is active, the user's next chat message is sent to
 * {@code TruFarChatService#submitQuizAnswer(quizId, answer)}.</p>
 */
public final class QuizPrompt {

    @NonNull
    private final String quizId;
    @NonNull
    private final String question;
    @NonNull
    private final List<String> options;
    private final int hpReward;

    /**
     * @param quizId   unique ID for this quiz instance (used for answer routing and replay protection).
     * @param question question text shown to the user.
     * @param options  multiple-choice options, or {@code null}/empty for a free-text answer.
     * @param hpReward HP awarded if passed (≥ 0).
     */
    public QuizPrompt(@NonNull String quizId,
                      @NonNull String question,
                      @Nullable List<String> options,
                      int hpReward) {
        this.quizId = quizId;
        this.question = question;
        this.options = options == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(options));
        this.hpReward = Math.max(0, hpReward);
    }

    @NonNull
    public String getQuizId() {
        return quizId;
    }

    @NonNull
    public String getQuestion() {
        return question;
    }

    /** @return unmodifiable options; empty means free-text answer. */
    @NonNull
    public List<String> getOptions() {
        return options;
    }

    public int getHpReward() {
        return hpReward;
    }

    @NonNull
    @Override
    public String toString() {
        return "QuizPrompt{" + quizId + ", reward=" + hpReward + "}";
    }
}
