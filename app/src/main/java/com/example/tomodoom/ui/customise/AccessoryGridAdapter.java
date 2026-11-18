package com.example.tomodoom.ui.customise;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.ItemAccessoryCircleBinding;
import com.example.tomodoom.model.AccessoryItem;

import java.util.List;
import java.util.Objects;

/**
 * The item circles for one slot: a "None" circle followed by the slot's items. The equipped item's
 * circle is selected. Showing another slot means creating a new adapter.
 */
final class AccessoryGridAdapter extends RecyclerView.Adapter<AccessoryGridAdapter.CircleHolder> {

    /** Called with the tapped item's ID, or {@code null} for the "None" circle. */
    interface OnItemClickListener {
        void onItemClick(@Nullable String itemId);
    }

    private static final Object PAYLOAD_SELECTION = new Object();
    private static final int NONE_POSITION = 0;

    @NonNull
    private final List<AccessoryItem> items;
    @NonNull
    private final String noneLabel;
    @NonNull
    private final OnItemClickListener listener;
    @Nullable
    private String equippedItemId;

    AccessoryGridAdapter(@NonNull List<AccessoryItem> items,
                         @NonNull String noneLabel,
                         @Nullable String equippedItemId,
                         @NonNull OnItemClickListener listener) {
        this.items = items;
        this.noneLabel = noneLabel;
        this.equippedItemId = equippedItemId;
        this.listener = listener;
    }

    /** Moves the selection ring to {@code itemId} without rebinding the images. */
    void setEquippedItem(@Nullable String itemId) {
        if (Objects.equals(equippedItemId, itemId)) {
            return;
        }
        int previous = positionOf(equippedItemId);
        equippedItemId = itemId;
        notifyItemChanged(previous, PAYLOAD_SELECTION);
        notifyItemChanged(positionOf(itemId), PAYLOAD_SELECTION);
    }

    private int positionOf(@Nullable String itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(itemId)) {
                return i + 1;
            }
        }
        return NONE_POSITION;
    }

    @Nullable
    private String itemIdAt(int position) {
        return position == NONE_POSITION ? null : items.get(position - 1).getId();
    }

    @NonNull
    @Override
    public CircleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CircleHolder(ItemAccessoryCircleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CircleHolder holder, int position) {
        String itemId = itemIdAt(position);
        if (itemId == null) {
            holder.binding.circle.setImageResource(R.drawable.ic_item_none_24dp);
            holder.binding.circle.setContentDescription(noneLabel);
        } else {
            holder.binding.circle.setImageResource(AccessoryThumbnails.forItem(itemId));
            holder.binding.circle.setContentDescription(items.get(position - 1).getDisplayName());
        }
        holder.binding.circle.setOnClickListener(v -> listener.onItemClick(itemId));
        bindSelection(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull CircleHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            bindSelection(holder, position);
        }
    }

    private void bindSelection(@NonNull CircleHolder holder, int position) {
        holder.binding.circle.setSelected(position == positionOf(equippedItemId));
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    static final class CircleHolder extends RecyclerView.ViewHolder {

        @NonNull
        final ItemAccessoryCircleBinding binding;

        CircleHolder(@NonNull ItemAccessoryCircleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
