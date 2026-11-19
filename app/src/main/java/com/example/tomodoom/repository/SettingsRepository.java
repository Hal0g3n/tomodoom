package com.example.tomodoom.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tomodoom.model.ChatTextScale;

/**
 * App preferences set on the Settings page (for now, the chatbot's text size). Follows the same
 * rules as {@link PetStateRepository} (CLAUDE.md §3.2): one instance in {@code AppContainer}, the
 * only reader/writer of the {@value #PREFS_NAME} prefs file, {@code synchronized} mutators
 * callable from any thread, publishing only with {@code postValue} under the lock, and
 * {@code apply()} writes.
 */
public final class SettingsRepository {

    private static final String TAG = "SettingsRepository";

    public static final String PREFS_NAME = "tomodoom_settings";
    static final String KEY_CHAT_TEXT_SCALE = "chat_text_scale";

    private final Object lock = new Object();
    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final MutableLiveData<Float> chatTextScaleLiveData;
    /** Latest value; written only under {@link #lock}. */
    private volatile float chatTextScale;

    public SettingsRepository(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        chatTextScale = ChatTextScale.nearestStep(
                prefs.getFloat(KEY_CHAT_TEXT_SCALE, ChatTextScale.DEFAULT));
        chatTextScaleLiveData = new MutableLiveData<>(chatTextScale);
    }

    /** The chat text size as a multiple of the default. Emissions arrive on the main thread. */
    @NonNull
    public LiveData<Float> getChatTextScale() {
        return chatTextScaleLiveData;
    }

    /** @return the latest chat text scale, from any thread. */
    @AnyThread
    public float getCurrentChatTextScale() {
        return chatTextScale;
    }

    /** Saves the chat text size, snapped to an offered {@link ChatTextScale} step. */
    @AnyThread
    public float setChatTextScale(float scale) {
        synchronized (lock) {
            float snapped = ChatTextScale.nearestStep(scale);
            if (snapped == chatTextScale) {
                return chatTextScale;
            }
            chatTextScale = snapped;
            prefs.edit().putFloat(KEY_CHAT_TEXT_SCALE, snapped).apply();
            chatTextScaleLiveData.postValue(snapped);
            Log.i(TAG, "Chat text scale: " + snapped);
            return snapped;
        }
    }
}
