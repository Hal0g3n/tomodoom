package com.example.tomodoom.stubs;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.R;
import com.example.tomodoom.contracts.ManiengineAssetRenderer;
import com.example.tomodoom.model.AccessoryCatalog;
import com.example.tomodoom.model.AccessoryItem;
import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.PetState;

/**
 * Placeholder for the Maniengine renderer: shows a single centered text block describing the pet
 * and its equipped items. It assumes no image or vector format.
 *
 * <p>To be replaced by the Maniengine Agent via {@code AppContainer#createAssetRenderer()}.</p>
 */
public final class PlaceholderManiengineRenderer implements ManiengineAssetRenderer {

    @Nullable
    private TextView placeholderView;
    @Nullable
    private PetState lastRendered;

    @MainThread
    @Override
    public void render(@NonNull ViewGroup container, @NonNull PetState state) {
        if (placeholderView == null || placeholderView.getParent() != container) {
            container.removeAllViews();
            placeholderView = createPlaceholderView(container.getContext());
            container.addView(placeholderView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            lastRendered = null;
        }
        if (state.equals(lastRendered)) {
            return; // idempotent: nothing changed since the last frame
        }
        placeholderView.setText(describe(container.getContext(), state));
        lastRendered = state;
    }

    @MainThread
    @Override
    public void release(@NonNull ViewGroup container) {
        if (placeholderView != null) {
            container.removeView(placeholderView);
        }
        placeholderView = null;
        lastRendered = null;
    }

    @NonNull
    private static TextView createPlaceholderView(@NonNull Context context) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setLineSpacing(0f, 1.2f);
        return view;
    }

    @NonNull
    private static String describe(@NonNull Context context, @NonNull PetState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.isFainted()
                ? context.getString(R.string.placeholder_pet_fainted, state.getPetName())
                : context.getString(R.string.placeholder_pet_name, state.getPetName()));
        sb.append("\n\n");
        for (String slot : AccessorySlot.ALL) {
            String itemId = state.getEquippedItem(slot);
            AccessoryItem item = AccessoryCatalog.findById(itemId);
            String value = item != null ? item.getDisplayName()
                    : itemId != null ? itemId
                    : context.getString(R.string.placeholder_slot_empty);
            sb.append(context.getString(R.string.placeholder_slot_line, capitalize(slot), value))
                    .append('\n');
        }
        sb.append('\n').append(context.getString(R.string.placeholder_renderer_pending));
        return sb.toString();
    }

    @NonNull
    private static String capitalize(@NonNull String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
