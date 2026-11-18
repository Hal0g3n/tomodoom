package com.example.tomodoom.trufar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTPS + JSON request helper shared by the TruFar chat clients. Blocking: call it only
 * from a background worker. Plain HTTP is refused.
 */
final class JsonHttpClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_RESPONSE_CHARACTERS = 1_000_000;
    private static final int MAX_ERROR_DETAIL_CHARACTERS = 300;

    @NonNull
    private final String baseUrl;
    @NonNull
    private final String bearerToken;
    @NonNull
    private final String label;
    private final int readTimeoutMillis;

    /**
     * @param baseUrl           HTTPS origin/base URL; trailing slashes are ignored.
     * @param bearerToken       sent as {@code Authorization: Bearer …} when non-empty.
     * @param label             service name used in error messages (e.g. "TruFar").
     * @param readTimeoutMillis socket read timeout (LLM completions can be slow).
     */
    JsonHttpClient(@NonNull String baseUrl, @NonNull String bearerToken,
                   @NonNull String label, int readTimeoutMillis) {
        this.baseUrl = trimTrailingSlashes(baseUrl.trim());
        this.bearerToken = bearerToken.trim();
        this.label = label;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /**
     * Sends {@code body} (or nothing when null) to {@code baseUrl + path} and parses the JSON
     * response. An empty or 204 response yields an empty object. A non-2xx status throws an
     * {@link IOException} that includes the server's {@code error.message} when it provides one.
     */
    @WorkerThread
    @NonNull
    JSONObject requestJson(@NonNull String method,
                           @NonNull String path,
                           @Nullable JSONObject body) throws IOException, JSONException {
        HttpURLConnection connection = openConnection(method, path, body);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException(label + " request failed with HTTP " + status
                        + errorDetail(connection.getErrorStream()));
            }
            if (status == HttpURLConnection.HTTP_NO_CONTENT) {
                return new JSONObject();
            }
            String response = readResponse(connection.getInputStream());
            return response.trim().isEmpty() ? new JSONObject() : new JSONObject(response);
        } finally {
            connection.disconnect();
        }
    }

    @WorkerThread
    @NonNull
    private HttpURLConnection openConnection(@NonNull String method,
                                             @NonNull String path,
                                             @Nullable JSONObject body) throws IOException {
        URL url = new URL(baseUrl + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException(label + " API URL must use HTTPS");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestProperty("Accept", "application/json");
        if (!bearerToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        return connection;
    }

    @WorkerThread
    @NonNull
    private String readResponse(@NonNull InputStream stream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                response.append(buffer, 0, count);
                if (response.length() > MAX_RESPONSE_CHARACTERS) {
                    throw new IOException(label + " response exceeded the size limit");
                }
            }
        }
        return response.toString();
    }

    /** Best-effort {@code ": <error.message>"} from an error body; empty when unavailable. */
    @WorkerThread
    @NonNull
    private String errorDetail(@Nullable InputStream errorStream) {
        if (errorStream == null) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(readResponse(errorStream));
            JSONObject error = json.optJSONObject("error");
            String message = error != null ? error.optString("message", "") : json.optString("message", "");
            message = message.trim();
            if (message.isEmpty()) {
                return "";
            }
            if (message.length() > MAX_ERROR_DETAIL_CHARACTERS) {
                message = message.substring(0, MAX_ERROR_DETAIL_CHARACTERS) + "…";
            }
            return ": " + message;
        } catch (IOException | JSONException ignored) {
            return "";
        }
    }

    @NonNull
    static String trimTrailingSlashes(@NonNull String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
