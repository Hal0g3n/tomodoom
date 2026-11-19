package com.example.tomodoom.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * Reads the {@code usage} object of an OpenAI-compatible chat completion
 * ({@code prompt_tokens}, {@code completion_tokens}, {@code total_tokens}). Pure Java.
 */
final class TokenUsageParser {

    /** Token counts of one response. Immutable. */
    static final class Usage {
        final long promptTokens;
        final long completionTokens;
        final long totalTokens;

        Usage(long promptTokens, long completionTokens, long totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }
    }

    private TokenUsageParser() {
    }

    /**
     * @return the response's usage, or {@code null} if it has none. Missing or negative counts
     * are 0; a missing total is prompt + completion.
     */
    @Nullable
    static Usage parse(@NonNull JSONObject response) {
        JSONObject usage = response.optJSONObject("usage");
        if (usage == null) {
            return null;
        }
        long prompt = Math.max(0, usage.optLong("prompt_tokens", 0));
        long completion = Math.max(0, usage.optLong("completion_tokens", 0));
        long total = Math.max(0, usage.optLong("total_tokens", 0));
        if (total == 0) {
            total = prompt + completion;
        }
        if (total == 0) {
            return null;
        }
        return new Usage(prompt, completion, total);
    }
}
