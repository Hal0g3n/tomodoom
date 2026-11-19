package com.example.tomodoom.music;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.tomodoom.R;
import com.example.tomodoom.model.MusicVolume;

import java.io.IOException;

/**
 * Loops the app's background music ({@code res/raw/calm_bgm.ogg}) while the app is in the
 * foreground. Leaving the app stops it and rewinds, so reopening the app starts the track from
 * the beginning. The volume comes from the Settings page ({@link MusicVolume}); 0 is silent.
 *
 * <p>Plays politely: it asks for audio focus before playing, pauses when another app takes it,
 * and ducks for notifications. Preparation is asynchronous, so nothing is decoded on the main
 * thread. Any player error just stops the music (logged), never the app.
 *
 * <p>App-scoped, created in {@code TomodoomApplication}. <b>Main thread only.</b>
 */
@MainThread
public final class BackgroundMusicPlayer {

    private static final String TAG = "BackgroundMusic";

    /** Gain multiplier while another app asks us to duck (e.g. a notification sound). */
    private static final float DUCK_FACTOR = 0.2f;

    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    @NonNull
    private final Context context;
    @Nullable
    private final AudioManager audioManager;
    @NonNull
    private final AudioManager.OnAudioFocusChangeListener focusListener = this::onFocusChange;
    @Nullable
    private Object focusRequest; // AudioFocusRequest on API 26+

    @Nullable
    private MediaPlayer player;
    private boolean prepared;
    private boolean failed;
    private boolean foreground;
    private int volumePercent = MusicVolume.DEFAULT;
    private boolean hasFocus;
    /** Another app took audio focus; wait for it back (transient) or the next foreground. */
    private boolean pausedForFocus;
    private boolean ducked;

    public BackgroundMusicPlayer(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    /** The app came to the foreground: play from wherever the track is (the start, after a leave). */
    public void onAppForegrounded() {
        foreground = true;
        pausedForFocus = false;
        failed = false; // try again after an earlier error
        update();
    }

    /** The app left the foreground: stop and rewind, so the next visit starts the track afresh. */
    public void onAppBackgrounded() {
        foreground = false;
        if (player != null && prepared) {
            if (player.isPlaying()) {
                player.pause();
            }
            player.seekTo(0);
        }
        abandonFocus();
    }

    /** Sets the volume in percent ({@link MusicVolume}); 0 pauses the music. */
    public void setVolumePercent(int percent) {
        volumePercent = MusicVolume.clamp(percent);
        applyVolume();
        update();
    }

    /** Plays or pauses to match the current state. */
    private void update() {
        boolean wanted = foreground && volumePercent > 0 && !failed;
        if (!wanted) {
            if (player != null && prepared && player.isPlaying()) {
                player.pause();
            }
            if (volumePercent == 0) {
                abandonFocus(); // let other apps play while we're silent
            }
            return;
        }
        if (player == null) {
            createPlayer(); // update() runs again once it is prepared
            return;
        }
        if (!prepared || pausedForFocus) {
            return;
        }
        if (!hasFocus && !requestFocus()) {
            return;
        }
        if (!player.isPlaying()) {
            player.start();
        }
    }

    private void createPlayer() {
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.calm_bgm)) {
            mp.setAudioAttributes(ATTRIBUTES);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "can't open the background music", e);
            mp.release();
            failed = true;
            return;
        }
        mp.setLooping(true);
        mp.setOnPreparedListener(p -> {
            if (p != player) {
                return;
            }
            prepared = true;
            applyVolume();
            update();
        });
        mp.setOnErrorListener((p, what, extra) -> {
            Log.w(TAG, "player error " + what + "/" + extra + "; music stopped");
            if (p == player) {
                releasePlayer();
                failed = true;
                abandonFocus();
            }
            return true;
        });
        player = mp;
        prepared = false;
        mp.prepareAsync();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
    }

    private void applyVolume() {
        if (player == null || !prepared) {
            return;
        }
        float gain = MusicVolume.gainFor(volumePercent) * (ducked ? DUCK_FACTOR : 1f);
        player.setVolume(gain, gain);
    }

    // ---- Audio focus ----

    private void onFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                pausedForFocus = false;
                ducked = false;
                applyVolume();
                update();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                ducked = true;
                applyVolume();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pausedForFocus = true;
                pauseIfPlaying();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Another app is playing for good: stay quiet until the user comes back to us.
                hasFocus = false;
                pausedForFocus = true;
                pauseIfPlaying();
                break;
            default:
                break;
        }
    }

    private void pauseIfPlaying() {
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
    }

    private boolean requestFocus() {
        if (audioManager == null) {
            hasFocus = true; // nothing to negotiate with
            return true;
        }
        int result = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? audioManager.requestAudioFocus(focusRequest())
                : requestFocusLegacy(audioManager);
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasFocus) {
            Log.i(TAG, "audio focus denied; music waits");
        }
        return hasFocus;
    }

    private void abandonFocus() {
        if (!hasFocus) {
            return;
        }
        hasFocus = false;
        ducked = false;
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest());
        } else {
            abandonFocusLegacy(audioManager);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @NonNull
    private AudioFocusRequest focusRequest() {
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(ATTRIBUTES)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
        }
        return (AudioFocusRequest) focusRequest;
    }

    @SuppressWarnings("deprecation") // the only API before Android 8
    private int requestFocusLegacy(@NonNull AudioManager manager) {
        return manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
    }

    @SuppressWarnings("deprecation") // the only API before Android 8
    private void abandonFocusLegacy(@NonNull AudioManager manager) {
        manager.abandonAudioFocus(focusListener);
    }
}
