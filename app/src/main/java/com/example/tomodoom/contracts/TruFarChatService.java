package com.example.tomodoom.contracts;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.model.McpToolResult;
import com.example.tomodoom.model.QuizEvaluation;
import com.example.tomodoom.model.QuizPrompt;

import java.util.Map;

/**
 * Contract for the <b>TruFar/MCP Agent</b>: client for the TruFar chatbot backend, which
 * converses with the user, issues structured quizzes, evaluates answers and may invoke the
 * app's MCP pet tools.
 *
 * <h3>Asynchrony</h3>
 * Every method returns immediately and must not block. Results are delivered through the
 * {@link Listener}. Failures are reported via {@link Listener#onError(Throwable)}, never thrown.
 *
 * <h3>Threading</h3>
 * <ul>
 *     <li>Methods may be called from any thread (in practice, the main thread).</li>
 *     <li>{@link Listener} callbacks <b>may be invoked on any thread</b>, and the mock uses a
 *     background thread on purpose. The sole listener ({@code repository.ChatRepository}) is
 *     thread-safe and publishes to the UI via LiveData.</li>
 *     <li>Implementations must do network I/O on their own executor, never the main thread.</li>
 * </ul>
 *
 * <h3>Session flow</h3>
 * <ol>
 *     <li>{@code ChatRepository} sets itself as listener, then calls {@link #connect()} once.</li>
 *     <li>The user's messages go to {@link #sendMessage(String)}, except while a
 *     {@link QuizPrompt} is active, when the next message goes to
 *     {@link #submitQuizAnswer(String, String)}.</li>
 *     <li>The backend answers with {@link Listener#onQuizEvaluated}. If passed, the app awards
 *     HP through {@link McpPetActionHandler#onQuizPassed}.</li>
 * </ol>
 *
 * <p>Default implementation: {@code stubs.MockTruFarChatService}.</p>
 */
public interface TruFarChatService {

    /**
     * Receives everything the backend produces. Callbacks may arrive on <b>any thread</b>, in
     * order per session.
     */
    interface Listener {

        /** A plain conversational reply from the bot. */
        @AnyThread
        void onBotMessage(@NonNull String text);

        /**
         * The bot asks a structured quiz question. Until the matching
         * {@link #onQuizEvaluated} arrives, the user's next message is routed to
         * {@link TruFarChatService#submitQuizAnswer(String, String)}. A new prompt replaces
         * any previously active one.
         */
        @AnyThread
        void onQuizPrompt(@NonNull QuizPrompt prompt);

        /**
         * The bot evaluated an answer. When {@link QuizEvaluation#isPassed()} is true, the
         * listener awards {@link QuizEvaluation#getHpReward()} via
         * {@link McpPetActionHandler#onQuizPassed(String, int)}.
         */
        @AnyThread
        void onQuizEvaluated(@NonNull QuizEvaluation evaluation);

        /**
         * The backend (acting as MCP client) invokes one of the app's pet tools. The listener
         * executes it <b>synchronously</b> via
         * {@link McpPetActionHandler#executeMcpTool(String, Map)} and returns the result, which
         * the implementation must relay back to the backend.
         *
         * <p>Must <b>not</b> be called on the main thread.</p>
         *
         * @param toolName one of {@link McpPetActionHandler#getSupportedToolNames()}.
         * @param params   JSON-style arguments (String, Number, Boolean, Map, List values).
         * @return never null; unknown tools or bad params yield {@link McpToolResult#error}.
         */
        @WorkerThread
        @NonNull
        McpToolResult onToolCallRequested(@NonNull String toolName, @NonNull Map<String, Object> params);

        /** A transport or backend failure. The session may still be usable. */
        @AnyThread
        void onError(@NonNull Throwable error);
    }

    /**
     * Registers the single listener, replacing any previous one. Pass {@code null} to detach.
     * Events produced while no listener is set are dropped.
     */
    @AnyThread
    void setListener(@Nullable Listener listener);

    /** Opens the session (e.g. a handshake or greeting). Idempotent. */
    @AnyThread
    void connect();

    /** Closes the session and releases network resources. Idempotent. */
    @AnyThread
    void disconnect();

    /** @return true between a successful {@link #connect()} and {@link #disconnect()}. */
    @AnyThread
    boolean isConnected();

    /**
     * Sends a free-form user message. The reply arrives via {@link Listener#onBotMessage} and/or
     * {@link Listener#onQuizPrompt}.
     *
     * @param text non-empty, already-trimmed user text.
     */
    @AnyThread
    void sendMessage(@NonNull String text);

    /**
     * Submits the user's answer to an active quiz. The verdict arrives via
     * {@link Listener#onQuizEvaluated}.
     *
     * @param quizId the {@link QuizPrompt#getQuizId()} being answered.
     * @param answer non-empty, already-trimmed user text.
     */
    @AnyThread
    void submitQuizAnswer(@NonNull String quizId, @NonNull String answer);
}
