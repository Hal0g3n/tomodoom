package com.example.tomodoom.trufar;

import android.content.Context;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.contracts.TruFarChatService;
import com.example.tomodoom.model.QuizEvaluation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a TruFar agent export (e.g. Kafka, {@code assets/trufar/kafka_agent.json}) against any
 * OpenAI-compatible {@code POST /chat/completions} endpoint: the agent's {@code pre_prompt} becomes
 * the system prompt, with its model, temperature and memory window.
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
public final class OpenAiCompatibleChatService implements TruFarChatService {

    private static final String TAG = "OpenAiChatService";
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
    @NonNull
    private final String modelOverride;
    @NonNull
    private final JsonHttpClient http;
    @NonNull
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "trufar-agent-chat");
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
     * @param agentAssetPath TruFar agent export inside {@code assets/}.
     * @param baseUrl        HTTPS base URL; {@value #COMPLETIONS_PATH} is appended.
     * @param apiKey         bearer token; may be empty for gateways without auth.
     * @param modelOverride  model name to use instead of the export's; empty keeps the export's.
     */
    public OpenAiCompatibleChatService(@NonNull Context context,
                                       @NonNull String agentAssetPath,
                                       @NonNull String baseUrl,
                                       @NonNull String apiKey,
                                       @NonNull String modelOverride) {
        this.appContext = context.getApplicationContext();
        this.agentAssetPath = agentAssetPath;
        this.modelOverride = modelOverride;
        this.http = new JsonHttpClient(baseUrl, apiKey, "TruFar agent", READ_TIMEOUT_MILLIS);
    }

    @AnyThread
    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** Loads the agent, starts a fresh conversation and sends the hidden greeting trigger. */
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
                    Log.i(TAG, "loaded " + profile);
                }
                history = new ChatHistoryWindow(profile.getMemoryWindowTurns());
                sessionId = UUID.randomUUID().toString();
                gradedAnswerCount = 0;
                connected = true;
                exchange(AgentProfile.GREETING_TRIGGER, true);
            } catch (IOException exception) {
                connected = false;
                connectRequested.set(false);
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
                reportError(new IllegalStateException("TruFar agent is not connected"));
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

    /** One request/response round trip; the turn is remembered only if it succeeds. */
    @WorkerThread
    private void exchange(@NonNull String userText, boolean isGreeting) throws IOException {
        AgentProfile currentProfile = profile;
        ChatHistoryWindow currentHistory = history;
        if (currentProfile == null || currentHistory == null) {
            throw new IOException("TruFar agent is not connected");
        }
        String reply;
        try {
            JSONObject response = http.requestJson("POST", COMPLETIONS_PATH,
                    buildRequest(currentProfile, currentHistory, userText));
            reply = extractReply(response);
        } catch (JSONException exception) {
            throw new IOException("TruFar agent returned an invalid response", exception);
        }
        currentHistory.addTurn(userText, reply);
        deliver(QuizMarkerParser.parse(reply), isGreeting);
    }

    @NonNull
    private static JSONObject buildRequest(@NonNull AgentProfile profile,
                                           @NonNull ChatHistoryWindow history,
                                           @NonNull String userText) throws JSONException {
        JSONArray messages = new JSONArray();
        messages.put(message("system", profile.getSystemPrompt() + APP_PROTOCOL_PROMPT));
        for (ChatHistoryWindow.Message past : history.getMessages()) {
            messages.put(message(past.getRole(), past.getContent()));
        }
        messages.put(message(ChatHistoryWindow.ROLE_USER, userText));
        JSONObject request = new JSONObject();
        request.put("model", profile.getModel());
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
        String quizId = "trufar-agent:" + sessionId + ":" + gradedAnswerCount;
        boolean passed = result.getVerdict() == QuizMarkerParser.Verdict.CORRECT;
        currentListener.onQuizEvaluated(
                new QuizEvaluation(quizId, passed, CORRECT_ANSWER_HP, result.getText()));
    }

    @AnyThread
    private void reportNotConnected() {
        worker.execute(() -> reportError(new IllegalStateException("TruFar agent is not connected")));
    }

    @AnyThread
    private void reportError(@NonNull Throwable error) {
        Log.w(TAG, "TruFar agent request failed", error);
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onError(error);
        }
    }
}
