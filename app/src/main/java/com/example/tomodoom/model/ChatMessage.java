package com.example.tomodoom.model;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One immutable entry in the chat transcript.
 *
 * <p>{@link #getId()} is unique within the process and stable for the lifetime of the message,
 * which is what the RecyclerView diffing uses.</p>
 */
public final class ChatMessage {

    /** Who produced the message; also selects the RecyclerView view type. */
    public enum Type {
        /** Typed by the user. */
        USER,
        /** Produced by the chatbot (including quiz prompts and feedback). */
        BOT,
        /** App/system notices, e.g. MCP tool results ("+10 HP awarded via MCP!") or errors. */
        SYSTEM
    }

    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private final long id;
    @NonNull
    private final Type type;
    @NonNull
    private final String text;
    private final long timestampMillis;

    public ChatMessage(long id, @NonNull Type type, @NonNull String text, long timestampMillis) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.timestampMillis = timestampMillis;
    }

    @NonNull
    public static ChatMessage user(@NonNull String text) {
        return create(Type.USER, text);
    }

    @NonNull
    public static ChatMessage bot(@NonNull String text) {
        return create(Type.BOT, text);
    }

    @NonNull
    public static ChatMessage system(@NonNull String text) {
        return create(Type.SYSTEM, text);
    }

    @NonNull
    private static ChatMessage create(@NonNull Type type, @NonNull String text) {
        return new ChatMessage(NEXT_ID.getAndIncrement(), type, text, System.currentTimeMillis());
    }

    public long getId() {
        return id;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getText() {
        return text;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage)) return false;
        ChatMessage other = (ChatMessage) o;
        return id == other.id
                && timestampMillis == other.timestampMillis
                && type == other.type
                && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + type.hashCode();
        result = 31 * result + text.hashCode();
        result = 31 * result + (int) (timestampMillis ^ (timestampMillis >>> 32));
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "ChatMessage{#" + id + " " + type + "}";
    }
}
