package com.example.tomodoom.model;

import androidx.annotation.NonNull;

/**
 * Result of evaluating the user's answer to a {@link QuizPrompt}, delivered by
 * {@code ChatService.Listener#onQuizEvaluated}.
 *
 * <p>When {@link #isPassed()} is true, {@code ChatRepository} invokes
 * {@code McpPetActionHandler#onQuizPassed(quizId, hpReward)}.</p>
 */
public final class QuizEvaluation {

    @NonNull
    private final String quizId;
    private final boolean passed;
    private final int hpReward;
    @NonNull
    private final String feedback;

    /**
     * @param quizId   the {@link QuizPrompt#getQuizId()} being evaluated.
     * @param passed   whether the answer was accepted.
     * @param hpReward HP to award; forced to 0 when not passed.
     * @param feedback bot feedback shown to the user (e.g. "Correct!").
     */
    public QuizEvaluation(@NonNull String quizId, boolean passed, int hpReward, @NonNull String feedback) {
        this.quizId = quizId;
        this.passed = passed;
        this.hpReward = passed ? Math.max(0, hpReward) : 0;
        this.feedback = feedback;
    }

    @NonNull
    public String getQuizId() {
        return quizId;
    }

    public boolean isPassed() {
        return passed;
    }

    public int getHpReward() {
        return hpReward;
    }

    @NonNull
    public String getFeedback() {
        return feedback;
    }

    @NonNull
    @Override
    public String toString() {
        return "QuizEvaluation{" + quizId + ", passed=" + passed + ", reward=" + hpReward + "}";
    }
}
