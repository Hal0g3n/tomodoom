package com.example.tomodoom.doomscroll;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.model.InstalledApp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Lists the apps that have a launcher icon, i.e. the apps a user could doom-scroll in, for the
 * watched-apps picker. Tomodoom itself is left out. Needs the {@code <queries>} launcher intent in
 * the manifest to see other apps on Android 11+. Loading labels reads every package, so call
 * {@link #get()} off the main thread.
 */
public final class LauncherAppsQuery implements Supplier<List<InstalledApp>> {

    @NonNull
    private final Context appContext;

    public LauncherAppsQuery(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @WorkerThread
    @NonNull
    @Override
    public List<InstalledApp> get() {
        PackageManager packageManager = appContext.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                : queryLegacy(packageManager, launcher);
        String ownPackage = appContext.getPackageName();
        Set<String> seen = new HashSet<>();
        List<InstalledApp> apps = new ArrayList<>();
        for (ResolveInfo info : activities) {
            if (info.activityInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (packageName == null || packageName.equals(ownPackage) || !seen.add(packageName)) {
                continue;
            }
            CharSequence label = info.activityInfo.applicationInfo != null
                    ? info.activityInfo.applicationInfo.loadLabel(packageManager)
                    : info.loadLabel(packageManager);
            apps.add(new InstalledApp(packageName, label.toString()));
        }
        return apps;
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private static List<ResolveInfo> queryLegacy(@NonNull PackageManager packageManager,
                                                 @NonNull Intent intent) {
        return packageManager.queryIntentActivities(intent, 0);
    }
}
