package com.example.tomodoom.doomscroll;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.example.tomodoom.R;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.di.AppContainer;

/**
 * Keeps the app process running while the user is in other apps, so Android doesn't freeze the
 * doom-scroll polling. The polling itself stays in {@link UsageStatsDoomScrollMonitor}.
 */
public final class DoomScrollMonitorService extends Service {

    private static final String TAG = "DoomScrollMonitorService";
    private static final String CHANNEL_ID = "doom_scroll_monitor";
    private static final int NOTIFICATION_ID = 1002;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
        } catch (IllegalStateException exception) {
            // Android 12+ refuses when a sticky restart happens in the background.
            Log.w(TAG, "Not allowed to run in the foreground right now", exception);
            stopSelf();
            return START_NOT_STICKY;
        }
        DoomScrollMonitor monitor = AppContainer.from(this).getDoomScrollMonitor();
        if (!monitor.isPermissionGranted()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        monitor.startMonitoring();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        AppContainer.from(this).getDoomScrollMonitor().stopMonitoring();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return null;
    }

    @NonNull
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                        getString(R.string.doom_scroll_monitor_channel),
                        NotificationManager.IMPORTANCE_LOW));
            }
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.doom_scroll_monitor_title))
                .setContentText(getString(R.string.doom_scroll_monitor_text))
                .setContentIntent(DoomScrollNotifier.openAppIntent(this))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
