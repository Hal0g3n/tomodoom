package com.example.tomodoom.contracts;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/**
 * Told about the tokens each successful LLM request used, for the Stats page.
 *
 * <p>A {@link ChatService} that talks to an LLM reports every request whose response included
 * token usage, with the model that actually answered (which may be a fallback). Failed attempts
 * report nothing. Callbacks may arrive on <b>any thread</b> and must return quickly.</p>
 */
public interface TokenUsageListener {

    /**
     * @param model            the model that answered.
     * @param promptTokens     input tokens (≥ 0).
     * @param completionTokens output tokens (≥ 0).
     * @param totalTokens      total as reported by the provider (≥ 0; may exceed the sum, e.g.
     *                         with reasoning tokens).
     */
    @AnyThread
    void onTokensUsed(@NonNull String model, long promptTokens, long completionTokens,
                      long totalTokens);
}
