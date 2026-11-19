package com.example.tomodoom.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.model.ChatMessage;
import com.example.tomodoom.model.QuizPrompt;
import com.example.tomodoom.repository.ChatRepository;
import com.example.tomodoom.repository.SettingsRepository;

import java.util.List;

/**
 * ViewModel for the Chat page. It is thin: the transcript and the quiz/MCP orchestration live in
 * the app-scoped {@link ChatRepository}, so they survive tab switches.
 */
public class ChatViewModel extends ViewModel {

    @NonNull
    private final ChatRepository chatRepository;
    @NonNull
    private final SettingsRepository settingsRepository;

    public ChatViewModel(@NonNull ChatRepository chatRepository,
                         @NonNull SettingsRepository settingsRepository) {
        this.chatRepository = chatRepository;
        this.settingsRepository = settingsRepository;
        chatRepository.start(); // idempotent; connects the chat session the first time
    }

    @NonNull
    public LiveData<List<ChatMessage>> getMessages() {
        return chatRepository.getMessages();
    }

    /** Non-null while the next message will be submitted as a quiz answer. */
    @NonNull
    public LiveData<QuizPrompt> getActiveQuiz() {
        return chatRepository.getActiveQuiz();
    }

    /** The chat text size chosen in Settings, as a multiple of the default. */
    @NonNull
    public LiveData<Float> getChatTextScale() {
        return settingsRepository.getChatTextScale();
    }

    public void sendMessage(@NonNull String text) {
        chatRepository.sendUserMessage(text);
    }
}
