package com.example.tomodoom.chat;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Retry policy for LLM requests whose provider is temporarily overloaded (e.g. Gemini's free tier
 * answering "HTTP 503: This model is currently experiencing high demand"). Pure Java, so it is
 * unit-tested on the JVM.
 *
 * <p>The attempt plan tries the primary model twice, then each fallback model once, capped at
 * {@link #MAX_ATTEMPTS}, waiting {@link #backoffMillisBefore(int)} between attempts. Only
 * {@link #isRetryable(IOException) temporary} failures move on to the next attempt, so a bad key
 * or an unknown model fails straight away, and a timeout moves straight on to the next model
 * ({@link #nextAttemptIndex}).
 */
final class ModelFallback {

    /** Upper bound on requests per user message (each may take up to the read timeout). */
    static final int MAX_ATTEMPTS = 4;

    private static final long FIRST_BACKOFF_MILLIS = 1_000;
    private static final long LATER_BACKOFF_MILLIS = 2_000;

    private ModelFallback() {
    }

    /**
     * Splits the {@code GEMINI_MODELS} value: a comma-separated list, primary first.
     * Blanks and duplicates are dropped; an empty value gives an empty list (keep the export's
     * model).
     */
    @NonNull
    static List<String> parseModels(@NonNull String raw) {
        List<String> models = new ArrayList<>();
        for (String part : raw.split(",")) {
            String model = part.trim();
            if (!model.isEmpty() && !models.contains(model)) {
                models.add(model);
            }
        }
        return Collections.unmodifiableList(models);
    }

    /** Primary twice, then each distinct fallback once, at most {@link #MAX_ATTEMPTS} entries. */
    @NonNull
    static List<String> attemptPlan(@NonNull String primary, @NonNull List<String> fallbacks) {
        List<String> plan = new ArrayList<>();
        plan.add(primary);
        plan.add(primary);
        for (String fallback : fallbacks) {
            if (plan.size() >= MAX_ATTEMPTS) {
                break;
            }
            if (!fallback.equals(primary) && !plan.contains(fallback)) {
                plan.add(fallback);
            }
        }
        return Collections.unmodifiableList(plan);
    }

    /**
     * Index of the attempt to make after {@code failedIndex} failed with {@code error}, or -1 to
     * give up. Non-retryable errors give up at once. A timeout skips the remaining attempts on
     * the same model (a model that hangs usually keeps hanging, and each try costs a full read
     * timeout), while quick "busy" answers (429/503…) retry it.
     */
    static int nextAttemptIndex(@NonNull List<String> plan, int failedIndex,
                                @NonNull IOException error) {
        if (!isRetryable(error)) {
            return -1;
        }
        int next = failedIndex + 1;
        if (error instanceof SocketTimeoutException) {
            String failedModel = plan.get(failedIndex);
            while (next < plan.size() && plan.get(next).equals(failedModel)) {
                next++;
            }
        }
        return next < plan.size() ? next : -1;
    }

    /** Wait before attempt {@code attemptIndex} (0-based): none before the first one. */
    static long backoffMillisBefore(int attemptIndex) {
        if (attemptIndex <= 0) {
            return 0;
        }
        return attemptIndex == 1 ? FIRST_BACKOFF_MILLIS : LATER_BACKOFF_MILLIS;
    }

    /**
     * True for failures that another attempt might fix: timeouts, rate limiting (429) and server
     * overload/outage (408, 500, 502, 503, 504). Client errors such as 400/401/403/404 are final.
     */
    static boolean isRetryable(@NonNull IOException error) {
        if (error instanceof SocketTimeoutException) {
            return true;
        }
        if (error instanceof HttpStatusException) {
            switch (((HttpStatusException) error).getStatusCode()) {
                case 408:
                case 429:
                case 500:
                case 502:
                case 503:
                case 504:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }
}
