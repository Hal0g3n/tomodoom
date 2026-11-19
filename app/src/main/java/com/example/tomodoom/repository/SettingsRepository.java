package com.example.tomodoom.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tomodoom.model.ChatTextScale;
import com.example.tomodoom.model.DoomScrollRate;
import com.example.tomodoom.model.MusicVolume;

/**
 * App preferences: the chatbot's text size and the background-music volume (Settings page), and
 * the HP lost per doom-scroll minute (debug panel). Follows the same
 * rules as {@link PetStateRepository} (CLAUDE.md §3.2): one instance in {@code AppContainer}, the
 * only reader/writer of the {@value #PREFS_NAME} prefs file, {@code synchronized} mutators
 * callable from any thread, publishing only with {@code postValue} under the lock, and
 * {@code apply()} writes.
 */
public final class SettingsRepository {

    private static final String TAG = "SettingsRepository";

    public static final String PREFS_NAME = "tomodoom_settings";
    static final String KEY_CHAT_TEXT_SCALE = "chat_text_scale";
    static final String KEY_MUSIC_VOLUME = "music_volume";
    static final String KEY_DOOM_SCROLL_HP_PER_MINUTE = "doom_scroll_hp_per_minute";

    private final Object lock = new Object();
    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final MutableLiveData<Float> chatTextScaleLiveData;
    /** Latest value; written only under {@link #lock}. */
    private volatile float chatTextScale;
    @NonNull
    private final MutableLiveData<Integer> musicVolumeLiveData;
    /** Latest value; written only under {@link #lock}. */
    private volatile int musicVolume;
    @NonNull
    private final MutableLiveData<Integer> doomScrollHpPerMinuteLiveData;
    /** Latest value; written only under {@link #lock}. */
    private volatile int doomScrollHpPerMinute;

    public SettingsRepository(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        chatTextScale = ChatTextScale.nearestStep(
                prefs.getFloat(KEY_CHAT_TEXT_SCALE, ChatTextScale.DEFAULT));
        chatTextScaleLiveData = new MutableLiveData<>(chatTextScale);
        musicVolume = MusicVolume.clamp(prefs.getInt(KEY_MUSIC_VOLUME, MusicVolume.DEFAULT));
        musicVolumeLiveData = new MutableLiveData<>(musicVolume);
        doomScrollHpPerMinute = DoomScrollRate.clamp(
                prefs.getInt(KEY_DOOM_SCROLL_HP_PER_MINUTE, DoomScrollRate.DEFAULT));
        doomScrollHpPerMinuteLiveData = new MutableLiveData<>(doomScrollHpPerMinute);
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

    /** The background-music volume in percent (0 = silent). Emissions arrive on the main thread. */
    @NonNull
    public LiveData<Integer> getMusicVolume() {
        return musicVolumeLiveData;
    }

    /** @return the latest background-music volume in percent, from any thread. */
    @AnyThread
    public int getCurrentMusicVolume() {
        return musicVolume;
    }

    /** Saves the background-music volume, snapped to a {@link MusicVolume} step. */
    @AnyThread
    public int setMusicVolume(int percent) {
        synchronized (lock) {
            int clamped = MusicVolume.clamp(percent);
            if (clamped == musicVolume) {
                return musicVolume;
            }
            musicVolume = clamped;
            prefs.edit().putInt(KEY_MUSIC_VOLUME, clamped).apply();
            musicVolumeLiveData.postValue(clamped);
            Log.i(TAG, "Music volume: " + clamped);
            return clamped;
        }
    }

    /** HP lost per counted doom-scroll minute. Emissions arrive on the main thread. */
    @NonNull
    public LiveData<Integer> getDoomScrollHpPerMinute() {
        return doomScrollHpPerMinuteLiveData;
    }

    /** @return the latest HP lost per doom-scroll minute, from any thread. */
    @AnyThread
    public int getCurrentDoomScrollHpPerMinute() {
        return doomScrollHpPerMinute;
    }

    /** Saves the HP lost per doom-scroll minute, clamped to the {@link DoomScrollRate} range. */
    @AnyThread
    public int setDoomScrollHpPerMinute(int hpPerMinute) {
        synchronized (lock) {
            int clamped = DoomScrollRate.clamp(hpPerMinute);
            if (clamped == doomScrollHpPerMinute) {
                return doomScrollHpPerMinute;
            }
            doomScrollHpPerMinute = clamped;
            prefs.edit().putInt(KEY_DOOM_SCROLL_HP_PER_MINUTE, clamped).apply();
            doomScrollHpPerMinuteLiveData.postValue(clamped);
            Log.i(TAG, "Doom-scroll HP per minute: " + clamped);
            return clamped;
        }
    }
}
