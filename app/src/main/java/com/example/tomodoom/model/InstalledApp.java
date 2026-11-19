package com.example.tomodoom.model;

import androidx.annotation.NonNull;

/** An app the user can open from the launcher, as listed in the watched-apps picker. */
public final class InstalledApp {

    @NonNull
    private final String packageName;
    @NonNull
    private final String label;

    public InstalledApp(@NonNull String packageName, @NonNull String label) {
        this.packageName = packageName;
        this.label = label;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InstalledApp)) {
            return false;
        }
        InstalledApp that = (InstalledApp) other;
        return packageName.equals(that.packageName) && label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return 31 * packageName.hashCode() + label.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "InstalledApp{" + packageName + ", " + label + "}";
    }
}
