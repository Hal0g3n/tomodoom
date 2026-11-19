package com.example.tomodoom;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.di.AppContainer;
import com.example.tomodoom.music.BackgroundMusicPlayer;
import com.example.tomodoom.repository.ChatRepository;
import com.example.tomodoom.repository.InactivityDecayTracker;

/**
 * Application entry point. It builds the {@link AppContainer}, starts doom-scroll monitoring
 * when the monitor already has its permissions, and tells {@link ChatRepository} when the app
 * goes to and comes back from the background (no activity started), so a long absence starts a
 * new conversation and costs the pet HP ({@link InactivityDecayTracker}). The same signal
 * starts and stops the {@link BackgroundMusicPlayer}, whose volume follows the Settings page.
 * The doom-scroll foreground service doesn't count as using the app.
 */
public class TomodoomApplication extends Application {

    private static final String TAG = "TomodoomApplication";

    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer(this);

        DoomScrollMonitor monitor = appContainer.getDoomScrollMonitor();
        if (monitor.isPermissionGranted()) {
            monitor.startMonitoring();
        } else {
            Log.i(TAG, "doom-scroll monitor not started: permission not granted yet");
        }

        BackgroundMusicPlayer music = new BackgroundMusicPlayer(this);
        // App-lifetime observer: the repository and the player live as long as the process.
        appContainer.getSettingsRepository().getMusicVolume().observeForever(volume -> {
            if (volume != null) {
                music.setVolumePercent(volume);
            }
        });

        registerActivityLifecycleCallbacks(new ForegroundTracker(appContainer.getChatRepository(),
                appContainer.getInactivityDecayTracker(), music));
    }

    @NonNull
    public AppContainer getAppContainer() {
        return appContainer;
    }

    /** Counts started activities: 1 → 0 is "backgrounded", 0 → 1 is "foregrounded". */
    private static final class ForegroundTracker implements ActivityLifecycleCallbacks {

        @NonNull
        private final ChatRepository chatRepository;
        @NonNull
        private final InactivityDecayTracker inactivityDecayTracker;
        @NonNull
        private final BackgroundMusicPlayer music;
        private int startedActivities;

        ForegroundTracker(@NonNull ChatRepository chatRepository,
                          @NonNull InactivityDecayTracker inactivityDecayTracker,
                          @NonNull BackgroundMusicPlayer music) {
            this.chatRepository = chatRepository;
            this.inactivityDecayTracker = inactivityDecayTracker;
            this.music = music;
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            if (startedActivities++ == 0) {
                // Wall clock for decay: the time away must survive a reboot.
                inactivityDecayTracker.onAppForegrounded(System.currentTimeMillis());
                chatRepository.onAppForegrounded(SystemClock.elapsedRealtime());
                music.onAppForegrounded();
            }
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            if (startedActivities > 0 && --startedActivities == 0) {
                inactivityDecayTracker.onAppBackgrounded(System.currentTimeMillis());
                chatRepository.onAppBackgrounded(SystemClock.elapsedRealtime());
                music.onAppBackgrounded(); // stops and rewinds: reopening starts the track afresh
            }
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
