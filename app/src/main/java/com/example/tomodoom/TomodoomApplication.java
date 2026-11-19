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
import com.example.tomodoom.repository.ChatRepository;

/**
 * Application entry point. It builds the {@link AppContainer}, starts doom-scroll monitoring
 * when the monitor already has its permissions, and tells {@link ChatRepository} when the app
 * goes to and comes back from the background (no activity started), so a long absence starts a
 * new conversation. The doom-scroll foreground service doesn't count as using the app.
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

        registerActivityLifecycleCallbacks(new ForegroundTracker(appContainer.getChatRepository()));
    }

    @NonNull
    public AppContainer getAppContainer() {
        return appContainer;
    }

    /** Counts started activities: 1 → 0 is "backgrounded", 0 → 1 is "foregrounded". */
    private static final class ForegroundTracker implements ActivityLifecycleCallbacks {

        @NonNull
        private final ChatRepository chatRepository;
        private int startedActivities;

        ForegroundTracker(@NonNull ChatRepository chatRepository) {
            this.chatRepository = chatRepository;
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            if (startedActivities++ == 0) {
                chatRepository.onAppForegrounded(SystemClock.elapsedRealtime());
            }
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            if (startedActivities > 0 && --startedActivities == 0) {
                chatRepository.onAppBackgrounded(SystemClock.elapsedRealtime());
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
