package com.example.tomodoom.ui.stats;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.example.tomodoom.R;
import com.example.tomodoom.stats.DurationSplit;

import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

/** Turns Stats numbers into display text, using {@code strings.xml}. */
final class StatsFormatter {

    /** Package name the doom-scroll log uses when the monitor didn't know the app. */
    private static final String UNKNOWN_PACKAGE = "unknown";

    private StatsFormatter() {
    }

    /** @return e.g. "under 1 min", "12 min", "2 h 15 min", "3 d 4 h". */
    @NonNull
    static String duration(@NonNull Context context, long millis) {
        if (millis <= 0) {
            return context.getString(R.string.stats_duration_minutes, 0);
        }
        DurationSplit split = DurationSplit.of(millis);
        int major = (int) Math.min(Integer.MAX_VALUE, split.getMajor());
        int minor = (int) split.getMinor();
        switch (split.getUnit()) {
            case MINUTES:
                return context.getString(R.string.stats_duration_minutes, major);
            case HOURS:
                return minor == 0 ? context.getString(R.string.stats_duration_hours_only, major)
                        : context.getString(R.string.stats_duration_hours, major, minor);
            case DAYS:
                return minor == 0 ? context.getString(R.string.stats_duration_days_only, major)
                        : context.getString(R.string.stats_duration_days, major, minor);
            case UNDER_MINUTE:
            default:
                return context.getString(R.string.stats_duration_under_minute);
        }
    }

    /** @return whole minutes as a short bar label, e.g. "12m"; empty for 0. */
    @NonNull
    static String shortMinutes(@NonNull Context context, long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes <= 0) {
            return millis > 0 ? context.getString(R.string.stats_bar_under_minute) : "";
        }
        if (minutes < 60) {
            return context.getString(R.string.stats_bar_minutes, (int) minutes);
        }
        return context.getString(R.string.stats_bar_hours, (int) (minutes / 60));
    }

    /** @return a token count with grouping separators, e.g. "12,345". */
    @NonNull
    static String number(long value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    /** @return the app's name, or its package name if it isn't visible to this app. */
    @NonNull
    static String appLabel(@NonNull Context context, @NonNull String packageName) {
        if (UNKNOWN_PACKAGE.equals(packageName)) {
            return context.getString(R.string.stats_unknown_app);
        }
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /** @return the app font for chart labels, or the default if it can't be loaded. */
    @NonNull
    static Typeface chartTypeface(@NonNull Context context) {
        try {
            Typeface font = ResourcesCompat.getFont(context, R.font.playpen_sans);
            if (font != null) {
                return font;
            }
        } catch (Resources.NotFoundException ignored) {
            // fall through (e.g. in the layout editor)
        }
        return Typeface.DEFAULT;
    }
}
