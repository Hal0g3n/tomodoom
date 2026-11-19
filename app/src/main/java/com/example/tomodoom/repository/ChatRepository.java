package com.example.tomodoom.repository;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tomodoom.contracts.McpPetActionHandler;
import com.example.tomodoom.contracts.ChatService;
import com.example.tomodoom.model.ChatMessage;
import com.example.tomodoom.model.McpToolResult;
import com.example.tomodoom.model.QuizEvaluation;
import com.example.tomodoom.model.QuizPrompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * App-scoped owner of the chat session. It keeps the transcript (in memory) and routes
 * {@link ChatService} events, including passed quizzes and backend tool calls, to the
 * {@link McpPetActionHandler}.
 *
 * <p>Because it is app-scoped, the transcript and quiz state survive tab switches and
 * configuration changes. It does <b>not</b> survive process death, and after
 * {@link #INACTIVITY_RESET_MILLIS} in the background the conversation starts over
 * ({@link #onAppForegrounded}).</p>
 *
 * <h3>Threading</h3>
 * Service callbacks arrive on any thread. All mutable state is guarded by {@link #lock}, and
 * LiveData is published only with {@code postValue} under that lock (same rule as
 * {@link PetStateRepository}). Calls into the MCP handler happen <em>outside</em> the lock.
 */
public final class ChatRepository {

    private static final String TAG = "ChatRepository";
    /** Oldest messages are dropped beyond this size to bound memory. */
    static final int MAX_TRANSCRIPT_SIZE = 500;
    /** Time in the background after which the next foregrounding starts a new conversation. */
    public static final long INACTIVITY_RESET_MILLIS = 10 * 60 * 1000L;

    @NonNull
    private final ChatService chatService;
    @NonNull
    private final McpPetActionHandler mcpHandler;

    private final Object lock = new Object();
    private final List<ChatMessage> transcript = new ArrayList<>();           // guarded by lock
    @Nullable
    private QuizPrompt activeQuiz;                                            // guarded by lock
    private final MutableLiveData<List<ChatMessage>> messagesLiveData =
            new MutableLiveData<>(Collections.<ChatMessage>emptyList());
    private final MutableLiveData<QuizPrompt> activeQuizLiveData = new MutableLiveData<>(null);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final BackgroundTimer backgroundTimer = new BackgroundTimer();

    public ChatRepository(@NonNull ChatService chatService, @NonNull McpPetActionHandler mcpHandler) {
        this.chatService = chatService;
        this.mcpHandler = mcpHandler;
        chatService.setListener(new ServiceListener());
    }

    /** Opens the chat session the first time it is called; later calls do nothing. */
    @AnyThread
    public void start() {
        if (started.compareAndSet(false, true)) {
            chatService.connect();
        }
    }

    /**
     * The app went to the background (no activity started).
     *
     * @param nowMs monotonic time, e.g. {@code SystemClock.elapsedRealtime()}.
     */
    @AnyThread
    public void onAppBackgrounded(long nowMs) {
        backgroundTimer.onBackground(nowMs);
    }

    /**
     * The app came back to the foreground. If it was away for at least
     * {@link #INACTIVITY_RESET_MILLIS} and the chat had been opened, starts a new conversation.
     *
     * @param nowMs monotonic time on the same clock as {@link #onAppBackgrounded}.
     */
    @AnyThread
    public void onAppForegrounded(long nowMs) {
        if (backgroundTimer.onForeground(nowMs, INACTIVITY_RESET_MILLIS) && started.get()) {
            startNewConversation();
        }
    }

    /**
     * Clears the transcript and any active quiz and opens a fresh session, which greets the user
     * again. The service is disconnected first, so replies still on their way for the old
     * conversation are dropped rather than landing in the new one.
     */
    @AnyThread
    public void startNewConversation() {
        Log.i(TAG, "starting a new conversation");
        chatService.disconnect();
        synchronized (lock) {
            transcript.clear();
            setActiveQuizLocked(null);
            messagesLiveData.postValue(Collections.<ChatMessage>emptyList());
        }
        chatService.connect();
    }

    /** Immutable snapshots of the transcript, oldest first. */
    @NonNull
    public LiveData<List<ChatMessage>> getMessages() {
        return messagesLiveData;
    }

    /** The quiz the user's next message will answer, or {@code null} when none is active. */
    @NonNull
    public LiveData<QuizPrompt> getActiveQuiz() {
        return activeQuizLiveData;
    }

    /**
     * Appends the user's message and forwards it: as a quiz answer if a quiz is active
     * (which consumes the quiz), otherwise as a normal message. Blank input is ignored.
     */
    @AnyThread
    public void sendUserMessage(@NonNull String rawText) {
        String text = rawText.trim();
        if (text.isEmpty()) {
            return;
        }
        String quizId = null;
        synchronized (lock) {
            appendLocked(ChatMessage.user(text));
            if (activeQuiz != null) {
                quizId = activeQuiz.getQuizId();
                setActiveQuizLocked(null); // one answer per prompt; the evaluation follows
            }
        }
        if (quizId != null) {
            chatService.submitQuizAnswer(quizId, text);
        } else {
            chatService.sendMessage(text);
        }
    }

    // ---------------------------------------------------------------------------------------

    private void append(@NonNull ChatMessage message) {
        synchronized (lock) {
            appendLocked(message);
        }
    }

    private void appendLocked(@NonNull ChatMessage message) {
        transcript.add(message);
        if (transcript.size() > MAX_TRANSCRIPT_SIZE) {
            transcript.subList(0, transcript.size() - MAX_TRANSCRIPT_SIZE).clear();
        }
        messagesLiveData.postValue(Collections.unmodifiableList(new ArrayList<>(transcript)));
    }

    private void setActiveQuizLocked(@Nullable QuizPrompt prompt) {
        activeQuiz = prompt;
        activeQuizLiveData.postValue(prompt);
    }

    @NonNull
    private static String formatQuiz(@NonNull QuizPrompt prompt) {
        StringBuilder sb = new StringBuilder("📝 Quiz (+").append(prompt.getHpReward()).append(" HP): ")
                .append(prompt.getQuestion());
        for (String option : prompt.getOptions()) {
            sb.append("\n• ").append(option);
        }
        return sb.toString();
    }

    @NonNull
    private static ChatMessage systemNotice(@NonNull McpToolResult result) {
        return ChatMessage.system(result.isSuccess() ? result.getMessage() : "⚠ " + result.getMessage());
    }

    /** Receives service callbacks on arbitrary threads. */
    private final class ServiceListener implements ChatService.Listener {

        @Override
        public void onBotMessage(@NonNull String text) {
            append(ChatMessage.bot(text));
        }

        @Override
        public void onQuizPrompt(@NonNull QuizPrompt prompt) {
            synchronized (lock) {
                setActiveQuizLocked(prompt);
                appendLocked(ChatMessage.bot(formatQuiz(prompt)));
            }
        }

        @Override
        public void onQuizEvaluated(@NonNull QuizEvaluation evaluation) {
            synchronized (lock) {
                if (activeQuiz != null && activeQuiz.getQuizId().equals(evaluation.getQuizId())) {
                    setActiveQuizLocked(null);
                }
                appendLocked(ChatMessage.bot(evaluation.getFeedback()));
            }
            if (evaluation.isPassed()) {
                McpToolResult result = mcpHandler.onQuizPassed(evaluation.getQuizId(), evaluation.getHpReward());
                Log.i(TAG, "quiz " + evaluation.getQuizId() + " passed -> " + result);
                append(systemNotice(result));
            }
        }

        @WorkerThread
        @NonNull
        @Override
        public McpToolResult onToolCallRequested(@NonNull String toolName, @NonNull Map<String, Object> params) {
            McpToolResult result = mcpHandler.executeMcpTool(toolName, params);
            Log.i(TAG, "MCP tool " + toolName + " -> " + result);
            append(ChatMessage.system("🔧 MCP " + toolName + ": "
                    + (result.isSuccess() ? "" : "⚠ ") + result.getMessage()));
            return result;
        }

        @Override
        public void onError(@NonNull Throwable error) {
            Log.w(TAG, "chat error", error);
            append(ChatMessage.system("⚠ Chat error: " + error.getMessage()));
        }
    }
}
