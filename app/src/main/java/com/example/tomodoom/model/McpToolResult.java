package com.example.tomodoom.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of an MCP pet tool invocation ({@code McpPetActionHandler}).
 *
 * <p>{@link #getMessage()} is human-readable and is shown in the chat as a SYSTEM message.
 * {@link #getData()} carries structured values (e.g. {@code hp}, {@code maxHp}) for the backend.
 * Values in {@code data} are restricted to JSON-friendly types: String, Number, Boolean,
 * or nested Map/List of those.</p>
 */
public final class McpToolResult {

    private final boolean success;
    @NonNull
    private final String message;
    @NonNull
    private final Map<String, Object> data;

    private McpToolResult(boolean success, @NonNull String message, @Nullable Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    @NonNull
    public static McpToolResult success(@NonNull String message, @Nullable Map<String, Object> data) {
        return new McpToolResult(true, message, data);
    }

    @NonNull
    public static McpToolResult error(@NonNull String message) {
        return new McpToolResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    /** @return unmodifiable structured payload (empty for errors). */
    @NonNull
    public Map<String, Object> getData() {
        return data;
    }

    @NonNull
    @Override
    public String toString() {
        return "McpToolResult{success=" + success + ", message=" + message + ", data=" + data + "}";
    }
}
