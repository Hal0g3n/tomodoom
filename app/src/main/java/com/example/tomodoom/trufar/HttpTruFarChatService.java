package com.example.tomodoom.trufar;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.contracts.McpPetActionHandler;
import com.example.tomodoom.contracts.TruFarChatService;
import com.example.tomodoom.model.McpToolResult;
import com.example.tomodoom.model.QuizEvaluation;
import com.example.tomodoom.model.QuizPrompt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTPS/JSON client for the versioned TruFar API documented in CLAUDE.md.
 * Network work and listener callbacks run on a private serial worker.
 */
public final class HttpTruFarChatService implements TruFarChatService {

    private static final String TAG = "HttpTruFarChatService";
    /** Long enough for a relayed TruFar turn: Kafka (deepseek-v4-pro) thinks before replying. */
    private static final int READ_TIMEOUT_MILLIS = 120_000;
    private static final int MAX_TOOL_ROUND_TRIPS = 8;

    private static final class ToolCallBudget {
        int calls;
    }

    @NonNull
    private final JsonHttpClient http;
    @NonNull
    private final List<String> supportedToolNames;
    @NonNull
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "trufar-chat");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull
    private final AtomicBoolean connectRequested = new AtomicBoolean(false);
    @Nullable
    private volatile Listener listener;
    @Nullable
    private String sessionId;
    private volatile boolean connected;

    public HttpTruFarChatService(@NonNull String baseUrl,
                                 @NonNull String bearerToken,
                                 @NonNull List<String> supportedToolNames) {
        this.http = new JsonHttpClient(baseUrl, bearerToken, "TruFar", READ_TIMEOUT_MILLIS);
        this.supportedToolNames = new ArrayList<>(supportedToolNames);
    }

    @AnyThread
    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @AnyThread
    @Override
    public void connect() {
        if (!connectRequested.compareAndSet(false, true)) {
            return;
        }
        worker.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("client", "tomodoom");
                request.put("tools", createToolDefinitions());
                JSONObject response = requestJson("POST", "/v1/chat/sessions", request);
                sessionId = requiredString(response, "session_id");
                connected = true;
                processEvents(requiredEvents(response), new ToolCallBudget());
            } catch (IOException | JSONException exception) {
                String openedSessionId = sessionId;
                sessionId = null;
                connected = false;
                connectRequested.set(false);
                if (openedSessionId != null) {
                    try {
                        requestJson("DELETE", sessionPath(openedSessionId), null);
                    } catch (IOException | JSONException cleanupException) {
                        exception.addSuppressed(cleanupException);
                    }
                }
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
            String currentSessionId = sessionId;
            sessionId = null;
            if (currentSessionId == null) {
                return;
            }
            try {
                requestJson("DELETE", sessionPath(currentSessionId), null);
            } catch (IOException | JSONException exception) {
                reportError(exception);
            } finally {
                connected = false;
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
            try {
                JSONObject request = new JSONObject();
                request.put("text", text);
                JSONObject response = requestJson(
                        "POST", sessionPath(requireSessionId()) + "/messages", request);
                processEvents(requiredEvents(response), new ToolCallBudget());
            } catch (IOException | JSONException exception) {
                reportError(exception);
            }
        });
    }

    @AnyThread
    @Override
    public void submitQuizAnswer(@NonNull String quizId, @NonNull String answer) {
        if (!connectRequested.get()) {
            reportNotConnected();
            return;
        }
        worker.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("quiz_id", quizId);
                request.put("answer", answer);
                JSONObject response = requestJson(
                        "POST", sessionPath(requireSessionId()) + "/quiz-answers", request);
                processEvents(requiredEvents(response), new ToolCallBudget());
            } catch (IOException | JSONException exception) {
                reportError(exception);
            }
        });
    }

    @WorkerThread
    private void processEvents(@NonNull JSONArray events, @NonNull ToolCallBudget toolCallBudget)
            throws IOException, JSONException {
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            String type = requiredString(event, "type");
            switch (type) {
                case "message":
                    notifyBotMessage(requiredString(event, "text"));
                    break;
                case "quiz_prompt":
                    notifyQuizPrompt(event);
                    break;
                case "quiz_evaluated":
                    notifyQuizEvaluation(event);
                    break;
                case "tool_call":
                    if (toolCallBudget.calls >= MAX_TOOL_ROUND_TRIPS) {
                        throw new IOException("TruFar exceeded the MCP tool-call limit");
                    }
                    toolCallBudget.calls++;
                    processToolCall(event, toolCallBudget);
                    break;
                case "error":
                    throw new IOException("TruFar reported an error: " + requiredString(event, "message"));
                default:
                    throw new IOException("TruFar returned an unsupported event type: " + type);
            }
        }
    }

    @WorkerThread
    private void notifyBotMessage(@NonNull String text) {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onBotMessage(text);
        }
    }

    @WorkerThread
    private void notifyQuizPrompt(@NonNull JSONObject event) throws JSONException {
        List<String> options = new ArrayList<>();
        Object optionsValue = event.opt("options");
        if (optionsValue != null && optionsValue != JSONObject.NULL) {
            if (!(optionsValue instanceof JSONArray)) {
                throw new JSONException("TruFar quiz options must be an array");
            }
            JSONArray jsonOptions = (JSONArray) optionsValue;
            for (int i = 0; i < jsonOptions.length(); i++) {
                options.add(jsonOptions.getString(i));
            }
        }
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onQuizPrompt(new QuizPrompt(
                    requiredString(event, "quiz_id"),
                    requiredString(event, "question"),
                    options,
                    event.getInt("hp_reward")));
        }
    }

    @WorkerThread
    private void notifyQuizEvaluation(@NonNull JSONObject event) throws JSONException {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onQuizEvaluated(new QuizEvaluation(
                    requiredString(event, "quiz_id"),
                    event.getBoolean("passed"),
                    event.getInt("hp_reward"),
                    requiredString(event, "feedback")));
        }
    }

    @WorkerThread
    private void processToolCall(@NonNull JSONObject event, @NonNull ToolCallBudget toolCallBudget)
            throws IOException, JSONException {
        String callId = requiredString(event, "call_id");
        String toolName = requiredString(event, "name");
        JSONObject arguments = event.optJSONObject("arguments");
        if (arguments == null) {
            throw new IOException("TruFar tool call has no arguments object");
        }
        Listener currentListener = listener;
        McpToolResult result = currentListener == null
                ? McpToolResult.error("No app listener is available for MCP tool calls.")
                : currentListener.onToolCallRequested(toolName, jsonObjectToMap(arguments));

        JSONObject resultJson = new JSONObject();
        resultJson.put("success", result.isSuccess());
        resultJson.put("message", result.getMessage());
        resultJson.put("data", new JSONObject(result.getData()));
        JSONObject request = new JSONObject();
        request.put("call_id", callId);
        request.put("result", resultJson);
        JSONObject response = requestJson(
                "POST", sessionPath(requireSessionId()) + "/tool-results", request);
        processEvents(requiredEvents(response), toolCallBudget);
    }

    @WorkerThread
    @NonNull
    private JSONArray createToolDefinitions() throws JSONException {
        JSONArray definitions = new JSONArray();
        for (String name : supportedToolNames) {
            JSONObject tool = new JSONObject();
            tool.put("name", name);
            switch (name) {
                case McpPetActionHandler.TOOL_AWARD_HP:
                    tool.put("description", "Award a bounded amount of HP to the pet.");
                    tool.put("input_schema", schema(
                            new String[]{"amount", "reason"},
                            new String[]{"amount"},
                            new String[]{"integer", "string"}));
                    break;
                case McpPetActionHandler.TOOL_EQUIP_ITEM:
                    tool.put("description", "Equip or unequip a pet accessory.");
                    tool.put("input_schema", schema(
                            new String[]{"slot", "itemId"},
                            new String[]{"slot"},
                            new String[]{"string", "string"}));
                    break;
                case McpPetActionHandler.TOOL_GET_PET_STATE:
                    tool.put("description", "Read the pet's current name, HP and equipped items.");
                    tool.put("input_schema", schema(
                            new String[0], new String[0], new String[0]));
                    break;
                default:
                    tool.put("description", "App pet action.");
                    tool.put("input_schema", schema(
                            new String[0], new String[0], new String[0]));
                    break;
            }
            definitions.put(tool);
        }
        return definitions;
    }

    @NonNull
    private static JSONObject schema(@NonNull String[] propertyNames,
                                     @NonNull String[] requiredNames,
                                     @NonNull String[] types) throws JSONException {
        JSONObject properties = new JSONObject();
        for (int i = 0; i < propertyNames.length; i++) {
            JSONObject property = new JSONObject();
            property.put("type", types[i]);
            properties.put(propertyNames[i], property);
        }
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", properties);
        JSONArray required = new JSONArray();
        for (String name : requiredNames) {
            required.put(name);
        }
        schema.put("required", required);
        return schema;
    }

    @WorkerThread
    @NonNull
    private JSONObject requestJson(@NonNull String method,
                                   @NonNull String path,
                                   @Nullable JSONObject body) throws IOException, JSONException {
        return http.requestJson(method, path, body);
    }

    @NonNull
    private String requireSessionId() throws IOException {
        String currentSessionId = sessionId;
        if (currentSessionId == null) {
            throw new IOException("TruFar session is not connected");
        }
        return currentSessionId;
    }

    @NonNull
    private static String sessionPath(@NonNull String sessionId) {
        return "/v1/chat/sessions/" + Uri.encode(sessionId);
    }

    @NonNull
    private static String requiredString(@NonNull JSONObject object, @NonNull String key)
            throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new JSONException("TruFar response is missing string field \"" + key + "\"");
        }
        return (String) value;
    }

    @NonNull
    private static JSONArray requiredEvents(@NonNull JSONObject response) throws JSONException {
        Object value = response.get("events");
        if (!(value instanceof JSONArray)) {
            throw new JSONException("TruFar response is missing an events array");
        }
        return (JSONArray) value;
    }

    @NonNull
    private static Map<String, Object> jsonObjectToMap(@NonNull JSONObject object)
            throws JSONException {
        Map<String, Object> result = new LinkedHashMap<>();
        JSONArray keys = object.names();
        if (keys == null) {
            return result;
        }
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i);
            result.put(key, jsonValue(object.get(key)));
        }
        return result;
    }

    @Nullable
    private static Object jsonValue(@NonNull Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            return jsonObjectToMap((JSONObject) value);
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                result.add(jsonValue(array.get(i)));
            }
            return result;
        }
        return value;
    }

    @AnyThread
    private void reportNotConnected() {
        worker.execute(() -> reportError(new IllegalStateException("TruFar session is not connected")));
    }

    @AnyThread
    private void reportError(@NonNull Throwable error) {
        Listener currentListener = listener;
        if (currentListener != null) {
            Log.w(TAG, "TruFar request failed", error);
            currentListener.onError(error);
        }
    }
}
