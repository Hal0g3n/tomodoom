package com.example.tomodoom.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.chat.PersonaSelector.Persona;
import com.example.tomodoom.contracts.ChatService;
import com.example.tomodoom.contracts.TokenUsageListener;
import com.example.tomodoom.model.PetState;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs the pet's two agent personas ({@code assets/agents/*.json}) on Google Gemini through its
 * OpenAI-compatible {@code POST /chat/completions} endpoint (any compatible endpoint works): an
 * agent's {@code pre_prompt} becomes the system prompt, with its model and temperature.
 *
 * <h3>Personas</h3>
 * Before every request the service reads the pet's state and lets {@link PersonaSelector} pick
 * the low-HP persona (Kafka) or the high-HP one (Charon), with hysteresis. Either persona speaks
 * as the pet: its own name in the prompt is replaced by the pet's name
 * ({@link AgentProfile#withPetName}). Both share one conversation memory, and the first reply
 * after a switch carries a hidden {@link PersonaPrompts#moodShiftNote}, so the change reads as
 * the pet's mood shifting rather than a new bot. The state is only read, never written: HP still
 * changes only through {@code McpPetActionHandler}. The persona in use is saved in the
 * {@value #PREFS_FILE} prefs file, so a new conversation (or a cold start) keeps the old
 * conversation's personality unless HP has crossed a threshold since.
 *
 * <h3>New conversations</h3>
 * {@link #disconnect()} followed by {@link #connect()} starts a fresh conversation (new memory,
 * session and quiz IDs, and a new greeting). Work queued or in flight for the old conversation
 * is dropped: each request carries the conversation generation it was made in, and its result
 * is discarded if {@link #disconnect()} has happened since.
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

    /** Chat-owned prefs file (not the pet-state file, which only PetStateRepository touches). */
    static final String PREFS_FILE = "tomodoom_chat";
    /** {@link Persona#getId()} of the persona last in use. */
    static final String KEY_PERSONA = "persona";

    /** HP requested for each answer the agent marks as correct. */
    public static final int CORRECT_ANSWER_HP = 10;

    /** Appended to the agent's own prompt; the user never sees it. */
    static final String APP_PROTOCOL_PROMPT = "\n\n# APP PROTOCOL (hidden from the user)\n"
            + "Whenever your reply judges the user's answer to a quiz question you asked, end the "
            + "reply with " + QuizMarkerParser.MARKER_CORRECT + " if the answer was right or "
            + QuizMarkerParser.MARKER_WRONG + " if it was wrong, alone on the last line. Use no "
            + "marker in any other reply, use at most one per reply, and never mention, explain "
            + "or quote the markers.\n"
            + "The chat renders Markdown. Write any maths in LaTeX, $...$ inline and $$...$$ on "
            + "its own line for display, and put any code in a fenced block with a language tag.";

    @NonNull
    private final Context appContext;
    @NonNull
    private final String lowHpAgentAsset;
    @NonNull
    private final String highHpAgentAsset;
    /** Read-only view of the pet (name and HP); any-thread safe. */
    @NonNull
    private final Supplier<PetState> petState;
    /** Primary model override; empty keeps the export's model. */
    @NonNull
    private final String modelOverride;
    /** Models tried after the primary when it is overloaded (see {@link ModelFallback}). */
    @NonNull
    private final List<String> fallbackModels;
    @NonNull
    private final JsonHttpClient http;
    /** Told the tokens of every successful request (Stats page); may be null. */
    @Nullable
    private final TokenUsageListener tokenUsageListener;
    @NonNull
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gemini-chat");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull
    private final AtomicBoolean connectRequested = new AtomicBoolean(false);
    /** Bumped by every {@link #disconnect()}; results from an older generation are dropped. */
    @NonNull
    private final AtomicInteger generation = new AtomicInteger();
    @Nullable
    private volatile Listener listener;
    private volatile boolean connected;

    // Worker-confined conversation state.
    @Nullable
    private AgentProfile lowHpProfile;
    @Nullable
    private AgentProfile highHpProfile;
    /** Created with the profiles, from the saved persona. */
    @Nullable
    private PersonaSelector personaSelector;
    @Nullable
    private ChatHistoryWindow history;
    @Nullable
    private String sessionId;
    private int gradedAnswerCount;

    /**
     * @param context          any context; only the application context is kept.
     * @param lowHpAgentAsset  agent export inside {@code assets/} used while HP is low.
     * @param highHpAgentAsset agent export inside {@code assets/} used while HP is high.
     * @param petState         current pet snapshot; called on the worker thread.
     * @param baseUrl          HTTPS base URL; {@value #COMPLETIONS_PATH} is appended.
     * @param apiKey           bearer token; may be empty for gateways without auth.
     * @param modelOverride    model to use instead of the export's, or a comma-separated list
     *                         (primary first, then fallbacks for when it is overloaded); empty
     *                         keeps the export's model.
     * @param tokenUsage       told the token usage of every successful request (any thread);
     *                         may be null.
     */
    public GeminiChatService(@NonNull Context context,
                             @NonNull String lowHpAgentAsset,
                             @NonNull String highHpAgentAsset,
                             @NonNull Supplier<PetState> petState,
                             @NonNull String baseUrl,
                             @NonNull String apiKey,
                             @NonNull String modelOverride,
                             @Nullable TokenUsageListener tokenUsage) {
        this.tokenUsageListener = tokenUsage;
        this.appContext = context.getApplicationContext();
        this.lowHpAgentAsset = lowHpAgentAsset;
        this.highHpAgentAsset = highHpAgentAsset;
        this.petState = petState;
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
     * Loads both agents, starts a fresh conversation and sends the hidden greeting trigger. Only a
     * failure to load the agents leaves the service disconnected: if just the greeting request
     * fails (e.g. the provider is overloaded), the error is reported but the session stays open,
     * so the user's next message starts the conversation.
     */
    @AnyThread
    @Override
    public void connect() {
        if (!connectRequested.compareAndSet(false, true)) {
            return;
        }
        final int gen = generation.get();
        worker.execute(() -> {
            if (isStale(gen)) {
                return;
            }
            try {
                if (lowHpProfile == null || highHpProfile == null || personaSelector == null) {
                    lowHpProfile = AgentProfileLoader.load(appContext, lowHpAgentAsset)
                            .withModelOverride(modelOverride);
                    highHpProfile = AgentProfileLoader.load(appContext, highHpAgentAsset)
                            .withModelOverride(modelOverride);
                    personaSelector = new PersonaSelector(
                            Persona.fromId(prefs().getString(KEY_PERSONA, null)));
                    Log.i(TAG, "loaded " + lowHpProfile + " and " + highHpProfile
                            + ", fallbacks " + fallbackModels
                            + ", saved persona " + personaSelector.getCurrent());
                }
            } catch (IOException exception) {
                connected = false;
                connectRequested.set(false);
                reportError(exception, gen);
                return;
            }
            history = new ChatHistoryWindow(Math.max(lowHpProfile.getMemoryWindowTurns(),
                    highHpProfile.getMemoryWindowTurns()));
            sessionId = UUID.randomUUID().toString();
            gradedAnswerCount = 0;
            connected = true;
            try {
                exchange(AgentProfile.GREETING_TRIGGER, true, gen);
            } catch (IOException exception) {
                reportError(exception, gen);
            }
        });
    }

    @AnyThread
    @Override
    public void disconnect() {
        generation.incrementAndGet();
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
        final int gen = generation.get();
        worker.execute(() -> {
            if (isStale(gen)) {
                return;
            }
            if (!connected) {
                reportError(new IllegalStateException("Gemini chat is not connected"), gen);
                return;
            }
            try {
                exchange(text, false, gen);
            } catch (IOException exception) {
                reportError(exception, gen);
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
     * fallbacks ({@link ModelFallback}); only the last failure is reported. A reply that arrives
     * after the conversation was replaced ({@code gen} is stale) is dropped.
     */
    @WorkerThread
    private void exchange(@NonNull String userText, boolean isGreeting, int gen)
            throws IOException {
        AgentProfile low = lowHpProfile;
        AgentProfile high = highHpProfile;
        PersonaSelector selector = personaSelector;
        ChatHistoryWindow currentHistory = history;
        if (low == null || high == null || selector == null || currentHistory == null) {
            throw new IOException("Gemini chat is not connected");
        }
        PetState pet = petState.get();
        Persona previous = selector.getCurrent();
        Persona persona = selector.select(pet.getCurrentHp());
        AgentProfile currentProfile = (persona == Persona.HIGH_HP ? high : low)
                .withPetName(pet.getPetName());
        // A new conversation simply opens in the current mood; only mid-chat switches get a note.
        String moodShift = isGreeting ? null : PersonaPrompts.moodShiftNote(
                previous, persona, pet.getCurrentHp(), pet.getMaxHp());
        List<String> plan = ModelFallback.attemptPlan(currentProfile.getModel(), fallbackModels);
        String reply = null;
        int attempt = 0;
        while (reply == null) {
            String model = plan.get(attempt);
            sleepBeforeAttempt(attempt);
            try {
                JSONObject response = http.requestJson("POST", COMPLETIONS_PATH,
                        buildRequest(currentProfile, moodShift, model, currentHistory, userText));
                // Tokens were spent even if the reply turns out stale or malformed.
                reportTokenUsage(model, response);
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
        if (isStale(gen)) {
            Log.i(TAG, "dropped a reply from a replaced conversation");
            return;
        }
        currentHistory.addTurn(userText, reply);
        if (previous != persona) {
            Log.i(TAG, "persona " + previous + " -> " + persona + " at " + pet.getCurrentHp()
                    + "/" + pet.getMaxHp() + " HP");
            prefs().edit().putString(KEY_PERSONA, persona.getId()).apply();
        }
        selector.commit(persona);
        deliver(QuizMarkerParser.parse(reply), isGreeting);
    }

    @AnyThread
    private void reportTokenUsage(@NonNull String model, @NonNull JSONObject response) {
        TokenUsageListener usageListener = tokenUsageListener;
        TokenUsageParser.Usage usage = TokenUsageParser.parse(response);
        if (usageListener != null && usage != null) {
            usageListener.onTokensUsed(model, usage.promptTokens, usage.completionTokens,
                    usage.totalTokens);
        }
    }

    private boolean isStale(int gen) {
        return gen != generation.get();
    }

    @NonNull
    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
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
                                           @Nullable String moodShift,
                                           @NonNull String model,
                                           @NonNull ChatHistoryWindow history,
                                           @NonNull String userText) throws JSONException {
        JSONArray messages = new JSONArray();
        String system = profile.getSystemPrompt() + (moodShift == null ? "" : moodShift)
                + APP_PROTOCOL_PROMPT;
        messages.put(message("system", system));
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

    /** Reports {@code error} unless it belongs to a conversation that has been replaced. */
    @AnyThread
    private void reportError(@NonNull Throwable error, int gen) {
        if (isStale(gen)) {
            return;
        }
        reportError(error);
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
