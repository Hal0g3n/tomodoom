package com.example.tomodoom.chat;

import android.content.Context;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.contracts.ChatService;
import com.example.tomodoom.model.QuizEvaluation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs an agent export (e.g. Kafka, {@code assets/agents/kafka_agent.json}) on Google Gemini
 * through its OpenAI-compatible {@code POST /chat/completions} endpoint (any compatible endpoint
 * works): the agent's {@code pre_prompt} becomes the system prompt, with its model, temperature
 * and memory window.
 *
 * <h3>Quizzes and HP</h3>
 * The agent quizzes the user in free text, so no {@code QuizPrompt}s are emitted. Instead,
 * {@link #APP_PROTOCOL_PROMPT} asks the model to end every reply that grades an answer with
 * {@link QuizMarkerParser#MARKER_CORRECT} or {@link QuizMarkerParser#MARKER_WRONG}. Such a reply
 * is delivered as {@link Listener#onQuizEvaluated} (marker stripped) with a session-unique quiz ID
 * and {@link #CORRECT_ANSWER_HP}, so the HP award goes through
 * {@code McpPetActionHandler.onQuizPassed} (replay guard and cap). Ungraded replies are delivered
 * as {@link Listener#onBotMessage}.
 *
 * <h3>Threading</h3>
 * Public methods return immediately. Asset loading, network I/O and every listener callback run
 * on a private serial worker; the conversation state below is confined to that worker.
 */
public final class GeminiChatService implements ChatService {

    private static final String TAG = "GeminiChatService";
    /** LLM completions (especially reasoning models) can take a while. */
    private static final int READ_TIMEOUT_MILLIS = 90_000;
    private static final String COMPLETIONS_PATH = "/chat/completions";

    /** HP requested for each answer the agent marks as correct. */
    public static final int CORRECT_ANSWER_HP = 10;

    /** Appended to the agent's own prompt; the user never sees it. */
    static final String APP_PROTOCOL_PROMPT = "\n\n# APP PROTOCOL (hidden from the user)\n"
            + "Whenever your reply judges the user's answer to a quiz question you asked, end the "
            + "reply with " + QuizMarkerParser.MARKER_CORRECT + " if the answer was right or "
            + QuizMarkerParser.MARKER_WRONG + " if it was wrong, alone on the last line. Use no "
            + "marker in any other reply, use at most one per reply, and never mention, explain "
            + "or quote the markers.";

    @NonNull
    private final Context appContext;
    @NonNull
    private final String agentAssetPath;
    /** Primary model override; empty keeps the export's model. */
    @NonNull
    private final String modelOverride;
    /** Models tried after the primary when it is overloaded (see {@link ModelFallback}). */
    @NonNull
    private final List<String> fallbackModels;
    @NonNull
    private final JsonHttpClient http;
    @NonNull
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gemini-chat");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull
    private final AtomicBoolean connectRequested = new AtomicBoolean(false);
    @Nullable
    private volatile Listener listener;
    private volatile boolean connected;

    // Worker-confined conversation state.
    @Nullable
    private AgentProfile profile;
    @Nullable
    private ChatHistoryWindow history;
    @Nullable
    private String sessionId;
    private int gradedAnswerCount;

    /**
     * @param context        any context; only the application context is kept.
     * @param agentAssetPath agent export inside {@code assets/}.
     * @param baseUrl        HTTPS base URL; {@value #COMPLETIONS_PATH} is appended.
     * @param apiKey         bearer token; may be empty for gateways without auth.
     * @param modelOverride  model to use instead of the export's, or a comma-separated list
     *                       (primary first, then fallbacks for when it is overloaded); empty keeps
     *                       the export's model.
     */
    public GeminiChatService(@NonNull Context context,
                                       @NonNull String agentAssetPath,
                                       @NonNull String baseUrl,
                                       @NonNull String apiKey,
                                       @NonNull String modelOverride) {
        this.appContext = context.getApplicationContext();
        this.agentAssetPath = agentAssetPath;
        List<String> models = ModelFallback.parseModels(modelOverride);
        this.modelOverride = models.isEmpty() ? "" : models.get(0);
        this.fallbackModels = models.size() > 1 ? models.subList(1, models.size())
                : Collections.<String>emptyList();
        this.http = new JsonHttpClient(baseUrl, apiKey, "Gemini", READ_TIMEOUT_MILLIS);
    }

    @AnyThread
    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Loads the agent, starts a fresh conversation and sends the hidden greeting trigger. Only a
     * failure to load the agent leaves the service disconnected: if just the greeting request
     * fails (e.g. the provider is overloaded), the error is reported but the session stays open,
     * so the user's next message starts the conversation ({@code ChatRepository} connects once).
     */
    @AnyThread
    @Override
    public void connect() {
        if (!connectRequested.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            try {
                if (profile == null) {
                    profile = AgentProfileLoader.load(appContext, agentAssetPath)
                            .withModelOverride(modelOverride);
                    Log.i(TAG, "loaded " + profile + ", fallbacks " + fallbackModels);
                }
            } catch (IOException exception) {
                connected = false;
                connectRequested.set(false);
                reportError(exception);
                return;
            }
            history = new ChatHistoryWindow(profile.getMemoryWindowTurns());
            sessionId = UUID.randomUUID().toString();
            gradedAnswerCount = 0;
            connected = true;
            try {
                exchange(AgentProfile.GREETING_TRIGGER, true);
            } catch (IOException exception) {
                reportError(exception);
            }
        });
    }

    @AnyThread
    @Override
    public void disconnect() {
        connectRequested.set(false);
        connected = false;
        worker.execute(() -> {
            connected = false;
            sessionId = null;
            if (history != null) {
                history.clear();
            }
        });
    }

    @AnyThread
    @Override
    public boolean isConnected() {
        return connected;
    }

    @AnyThread
    @Override
    public void sendMessage(@NonNull String text) {
        if (!connectRequested.get()) {
            reportNotConnected();
            return;
        }
        worker.execute(() -> {
            if (!connected) {
                reportError(new IllegalStateException("Gemini chat is not connected"));
                return;
            }
            try {
                exchange(text, false);
            } catch (IOException exception) {
                reportError(exception);
            }
        });
    }

    /**
     * Never triggered in practice (this service emits no {@code QuizPrompt}s); a stray answer is
     * simply sent as a normal message, which the agent grades in-conversation.
     */
    @AnyThread
    @Override
    public void submitQuizAnswer(@NonNull String quizId, @NonNull String answer) {
        sendMessage(answer);
    }

    /**
     * One user turn; the turn is remembered only if it succeeds. Temporary failures (overload,
     * rate limit, timeout) are retried with backoff, first on the primary model and then on the
     * fallbacks ({@link ModelFallback}); only the last failure is reported.
     */
    @WorkerThread
    private void exchange(@NonNull String userText, boolean isGreeting) throws IOException {
        AgentProfile currentProfile = profile;
        ChatHistoryWindow currentHistory = history;
        if (currentProfile == null || currentHistory == null) {
            throw new IOException("Gemini chat is not connected");
        }
        List<String> plan = ModelFallback.attemptPlan(currentProfile.getModel(), fallbackModels);
        String reply = null;
        int attempt = 0;
        while (reply == null) {
            String model = plan.get(attempt);
            sleepBeforeAttempt(attempt);
            try {
                JSONObject response = http.requestJson("POST", COMPLETIONS_PATH,
                        buildRequest(currentProfile, model, currentHistory, userText));
                reply = extractReply(response);
                if (attempt > 0) {
                    Log.i(TAG, "attempt " + (attempt + 1) + " succeeded with " + model);
                }
            } catch (JSONException exception) {
                throw new IOException("Gemini returned an invalid response", exception);
            } catch (IOException exception) {
                int next = ModelFallback.nextAttemptIndex(plan, attempt, exception);
                if (next < 0) {
                    throw exception;
                }
                Log.w(TAG, "attempt " + (attempt + 1) + " with " + model + " failed ("
                        + describe(exception) + "); retrying with " + plan.get(next));
                attempt = next;
            }
        }
        currentHistory.addTurn(userText, reply);
        deliver(QuizMarkerParser.parse(reply), isGreeting);
    }

    @WorkerThread
    private static void sleepBeforeAttempt(int attempt) throws IOException {
        long delay = ModelFallback.backoffMillisBefore(attempt);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini request was interrupted", exception);
        }
    }

    /** Status code or exception type only; never message content. */
    @NonNull
    private static String describe(@NonNull IOException exception) {
        if (exception instanceof HttpStatusException) {
            return "HTTP " + ((HttpStatusException) exception).getStatusCode();
        }
        return exception.getClass().getSimpleName();
    }

    @NonNull
    private static JSONObject buildRequest(@NonNull AgentProfile profile,
                                           @NonNull String model,
                                           @NonNull ChatHistoryWindow history,
                                           @NonNull String userText) throws JSONException {
        JSONArray messages = new JSONArray();
        messages.put(message("system", profile.getSystemPrompt() + APP_PROTOCOL_PROMPT));
        for (ChatHistoryWindow.Message past : history.getMessages()) {
            messages.put(message(past.getRole(), past.getContent()));
        }
        messages.put(message(ChatHistoryWindow.ROLE_USER, userText));
        JSONObject request = new JSONObject();
        request.put("model", model);
        request.put("temperature", profile.getTemperature());
        request.put("messages", messages);
        request.put("stream", false);
        return request;
    }

    @NonNull
    private static JSONObject message(@NonNull String role, @NonNull String content)
            throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    @NonNull
    private static String extractReply(@NonNull JSONObject response) throws JSONException {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new JSONException("response has no choices");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String content = message == null || message.isNull("content")
                ? "" : message.optString("content", "");
        if (content.trim().isEmpty()) {
            throw new JSONException("response has an empty message");
        }
        return content;
    }

    @WorkerThread
    private void deliver(@NonNull QuizMarkerParser.Result result, boolean isGreeting) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        // The greeting can't grade anything, so a stray marker there earns nothing.
        if (isGreeting || result.getVerdict() == QuizMarkerParser.Verdict.NONE) {
            currentListener.onBotMessage(result.getText());
            return;
        }
        gradedAnswerCount++;
        String quizId = "gemini-chat:" + sessionId + ":" + gradedAnswerCount;
        boolean passed = result.getVerdict() == QuizMarkerParser.Verdict.CORRECT;
        currentListener.onQuizEvaluated(
                new QuizEvaluation(quizId, passed, CORRECT_ANSWER_HP, result.getText()));
    }

    @AnyThread
    private void reportNotConnected() {
        worker.execute(() -> reportError(new IllegalStateException("Gemini chat is not connected")));
    }

    @AnyThread
    private void reportError(@NonNull Throwable error) {
        Log.w(TAG, "Gemini request failed", error);
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onError(error);
        }
    }
}
