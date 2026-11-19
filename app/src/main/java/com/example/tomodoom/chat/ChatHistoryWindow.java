package com.example.tomodoom.chat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Rolling conversation memory: keeps only the last {@code maxTurns} user/assistant exchanges,
 * mirroring the agent export's {@code chat_memory_window.window_size}. Not thread-safe: confine it
 * to one worker thread.
 */
public final class ChatHistoryWindow {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** One chat-completions message. */
    public static final class Message {
        @NonNull
        private final String role;
        @NonNull
        private final String content;

        public Message(@NonNull String role, @NonNull String content) {
            this.role = role;
            this.content = content;
        }

        @NonNull
        public String getRole() {
            return role;
        }

        @NonNull
        public String getContent() {
            return content;
        }
    }

    private final int maxTurns;
    /** Each element is one turn: [user, assistant]. */
    private final LinkedList<Message[]> turns = new LinkedList<>();

    /** @param maxTurns turns to keep; values below 1 are treated as 1. */
    public ChatHistoryWindow(int maxTurns) {
        this.maxTurns = Math.max(1, maxTurns);
    }

    /** Records a completed exchange, evicting the oldest turn beyond the window. */
    public void addTurn(@NonNull String userText, @NonNull String assistantText) {
        turns.addLast(new Message[]{
                new Message(ROLE_USER, userText),
                new Message(ROLE_ASSISTANT, assistantText)});
        while (turns.size() > maxTurns) {
            turns.removeFirst();
        }
    }

    /** The remembered messages, oldest first. */
    @NonNull
    public List<Message> getMessages() {
        List<Message> messages = new ArrayList<>(turns.size() * 2);
        for (Message[] turn : turns) {
            messages.add(turn[0]);
            messages.add(turn[1]);
        }
        return Collections.unmodifiableList(messages);
    }

    public int getTurnCount() {
        return turns.size();
    }

    public void clear() {
        turns.clear();
    }
}
