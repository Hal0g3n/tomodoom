package com.example.tomodoom.doomscroll;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.tomodoom.R;
import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.model.DoomScrollEvent;

/**
 * Forwards penalties to the wrapped listener and shows a notification at each warning milestone
 * (10, 20, 40 … HP lost in a session).
 * Without the notification permission, Android drops the warning and penalties still apply.
 */
public final class DoomScrollNotifier implements DoomScrollListener {

    private static final String WARNING_CHANNEL_ID = "doom_scroll_warnings";
    private static final int WARNING_NOTIFICATION_ID = 1001;

    @NonNull
    private final Context appContext;
    @NonNull
    private final DoomScrollListener delegate;

    public DoomScrollNotifier(@NonNull Context context, @NonNull DoomScrollListener delegate) {
        appContext = context.getApplicationContext();
        this.delegate = delegate;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = appContext.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(WARNING_CHANNEL_ID,
                        appContext.getString(R.string.doom_scroll_warning_channel),
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
        }
    }

    @AnyThread
    @Override
    public void onDoomScrollDetected(@NonNull DoomScrollEvent event) {
        delegate.onDoomScrollDetected(event);
    }

    @AnyThread
    @Override
    public void onDoomScrollWarning(@NonNull String sourcePackage, long sessionMillis,
                                    int sessionHpLost) {
        delegate.onDoomScrollWarning(sourcePackage, sessionMillis, sessionHpLost);
        NotificationManager manager =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        manager.notify(WARNING_NOTIFICATION_ID,
                new NotificationCompat.Builder(appContext, WARNING_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(appContext.getString(R.string.doom_scroll_warning_title))
                        .setContentText(appContext.getResources().getQuantityString(
                                R.plurals.doom_scroll_warning_text, sessionHpLost,
                                sessionHpLost))
                        .setContentIntent(openAppIntent(appContext))
                        .setAutoCancel(true)
                        .build());
    }

    /** @return an intent that opens the app at its launcher screen, if it has one. */
    @Nullable
    static PendingIntent openAppIntent(@NonNull Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        return launch == null ? null : PendingIntent.getActivity(
                context, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
