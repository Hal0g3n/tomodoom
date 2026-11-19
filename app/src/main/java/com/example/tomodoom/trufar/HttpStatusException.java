package com.example.tomodoom.trufar;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * A non-2xx HTTP response from {@link JsonHttpClient}. The message is the user-facing text shown
 * in the chat ("… request failed with HTTP 503: …"); {@link #getStatusCode()} lets callers decide
 * whether retrying could help (see {@link ModelFallback#isRetryable(IOException)}).
 */
final class HttpStatusException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    HttpStatusException(int statusCode, @NonNull String message) {
        super(message);
        this.statusCode = statusCode;
    }

    int getStatusCode() {
        return statusCode;
    }
}
