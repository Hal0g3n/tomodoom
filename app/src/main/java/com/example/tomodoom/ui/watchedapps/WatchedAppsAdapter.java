package com.example.tomodoom.ui.watchedapps;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tomodoom.databinding.ItemWatchedAppBinding;
import com.example.tomodoom.model.InstalledApp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rows of the watched-apps picker: icon, label and a switch. */
final class WatchedAppsAdapter extends RecyclerView.Adapter<WatchedAppsAdapter.Holder> {

    interface OnToggle {
        void onToggle(@NonNull String packageName, boolean watched);
    }

    private static final Object PAYLOAD_WATCHED = new Object();

    @NonNull
    private final PackageManager packageManager;
    @NonNull
    private final OnToggle onToggle;
    @NonNull
    private final Map<String, Drawable> icons = new HashMap<>();
    @NonNull
    private List<InstalledApp> apps = Collections.emptyList();
    @NonNull
    private Set<String> watched = Collections.emptySet();

    WatchedAppsAdapter(@NonNull PackageManager packageManager, @NonNull OnToggle onToggle) {
        this.packageManager = packageManager;
        this.onToggle = onToggle;
    }

    @SuppressLint("NotifyDataSetChanged") // The list is set once, when it has loaded.
    void setApps(@NonNull List<InstalledApp> apps) {
        this.apps = apps;
        notifyDataSetChanged();
    }

    void setWatched(@NonNull Set<String> watched) {
        this.watched = watched;
        notifyItemRangeChanged(0, apps.size(), PAYLOAD_WATCHED);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWatchedAppBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_WATCHED)) {
            bindSwitch(holder, apps.get(position));
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InstalledApp app = apps.get(position);
        holder.binding.watchedAppLabel.setText(app.getLabel());
        holder.binding.watchedAppIcon.setImageDrawable(iconFor(app.getPackageName()));
        holder.binding.watchedAppRow.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) {
                return;
            }
            String packageName = apps.get(current).getPackageName();
            onToggle.onToggle(packageName, !watched.contains(packageName));
        });
        bindSwitch(holder, app);
    }

    private void bindSwitch(@NonNull Holder holder, @NonNull InstalledApp app) {
        boolean isWatched = watched.contains(app.getPackageName());
        holder.binding.watchedAppSwitch.setChecked(isWatched);
        holder.binding.watchedAppRow.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                          @NonNull AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(isWatched);
            }
        });
    }

    /** Icons are cached for the picker's lifetime; the package manager caches them too. */
    @Nullable
    private Drawable iconFor(@NonNull String packageName) {
        if (icons.containsKey(packageName)) {
            return icons.get(packageName);
        }
        Drawable icon;
        try {
            icon = packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException exception) {
            icon = null;
        }
        icons.put(packageName, icon);
        return icon;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        @NonNull
        final ItemWatchedAppBinding binding;

        Holder(@NonNull ItemWatchedAppBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
