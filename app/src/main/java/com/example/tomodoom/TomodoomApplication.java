package com.example.tomodoom;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.di.AppContainer;

/**
 * Application entry point. It builds the {@link AppContainer} and starts doom-scroll monitoring
 * when the monitor already has its permissions.
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
    }

    @NonNull
    public AppContainer getAppContainer() {
        return appContainer;
    }
}
