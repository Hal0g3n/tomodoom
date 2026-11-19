package com.example.tomodoom.chat;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads an agent export (the JSON downloaded from the TruFar agent editor, the only TruFar
 * piece still used) from the APK assets. Disk I/O: call it off the main thread.
 *
 * <p>Fields used: {@code agent_basic_info.name}, {@code config.pre_prompt},
 * {@code config.model.name}, {@code config.model.params.temperature},
 * {@code config.chat_memory_window.window_size}. Everything else (icon, tools, opening
 * statement…) is ignored.</p>
 */
public final class AgentProfileLoader {

    private static final int MAX_ASSET_BYTES = 512 * 1024;

    private AgentProfileLoader() {
    }

    @WorkerThread
    @NonNull
    public static AgentProfile load(@NonNull Context context, @NonNull String assetPath)
            throws IOException {
        try (InputStream input = context.getAssets().open(assetPath)) {
            return parse(new JSONObject(readAll(input)));
        } catch (JSONException exception) {
            throw new IOException("Agent config " + assetPath + " is not valid JSON", exception);
        }
    }

    @NonNull
    static AgentProfile parse(@NonNull JSONObject root) throws IOException {
        JSONObject info = root.optJSONObject("agent_basic_info");
        JSONObject config = root.optJSONObject("config");
        if (config == null) {
            throw new IOException("Agent config has no \"config\" object");
        }
        String prompt = config.optString("pre_prompt", "").trim();
        if (prompt.isEmpty()) {
            throw new IOException("Agent config has an empty pre_prompt");
        }
        JSONObject model = config.optJSONObject("model");
        JSONObject params = model == null ? null : model.optJSONObject("params");
        JSONObject memory = config.optJSONObject("chat_memory_window");
        return new AgentProfile(
                info == null ? "" : info.optString("name", ""),
                prompt,
                model == null ? "" : model.optString("name", ""),
                params == null ? AgentProfile.DEFAULT_TEMPERATURE
                        : params.optDouble("temperature", AgentProfile.DEFAULT_TEMPERATURE),
                memory == null ? AgentProfile.DEFAULT_MEMORY_WINDOW_TURNS
                        : memory.optInt("window_size", AgentProfile.DEFAULT_MEMORY_WINDOW_TURNS));
    }

    @NonNull
    private static String readAll(@NonNull InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
            if (output.size() > MAX_ASSET_BYTES) {
                throw new IOException("Agent config exceeds " + MAX_ASSET_BYTES + " bytes");
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
