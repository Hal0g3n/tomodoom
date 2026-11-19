package com.example.tomodoom.ui.onboarding;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tomodoom.databinding.ItemPetChoiceBinding;

import java.util.List;
import java.util.Objects;

/**
 * The pet portrait cards on the onboarding screen and the Settings page; the chosen species' card
 * is selected.
 */
public final class PetChoiceAdapter extends RecyclerView.Adapter<PetChoiceAdapter.ChoiceHolder> {

    /** Called with the tapped card's species ID. */
    public interface OnSpeciesClickListener {
        void onSpeciesClick(@NonNull String species);
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    @NonNull
    private final List<String> species;
    @NonNull
    private final OnSpeciesClickListener listener;
    @Nullable
    private String selected;

    public PetChoiceAdapter(@NonNull List<String> species, @NonNull OnSpeciesClickListener listener) {
        this.species = species;
        this.listener = listener;
    }

    /** Moves the selection ring to {@code id} without rebinding the images. */
    public void setSelected(@Nullable String id) {
        if (Objects.equals(selected, id)) {
            return;
        }
        int previous = species.indexOf(selected);
        selected = id;
        if (previous >= 0) {
            notifyItemChanged(previous, PAYLOAD_SELECTION);
        }
        int next = species.indexOf(id);
        if (next >= 0) {
            notifyItemChanged(next, PAYLOAD_SELECTION);
        }
    }

    @NonNull
    @Override
    public ChoiceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPetChoiceBinding binding = ItemPetChoiceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        binding.portrait.setClipToOutline(true); // keep the image inside the rounded card
        return new ChoiceHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChoiceHolder holder, int position) {
        String id = species.get(position);
        holder.binding.portrait.setImageResource(PetPortraits.portraitFor(id));
        int name = PetPortraits.nameFor(id);
        if (name != 0) {
            holder.binding.name.setText(name);
        } else {
            holder.binding.name.setText(id);
        }
        holder.binding.choice.setOnClickListener(v -> listener.onSpeciesClick(id));
        bindSelection(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ChoiceHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            bindSelection(holder, position);
        }
    }

    private void bindSelection(@NonNull ChoiceHolder holder, int position) {
        holder.binding.choice.setSelected(species.get(position).equals(selected));
    }

    @Override
    public int getItemCount() {
        return species.size();
    }

    public static final class ChoiceHolder extends RecyclerView.ViewHolder {

        @NonNull
        final ItemPetChoiceBinding binding;

        ChoiceHolder(@NonNull ItemPetChoiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
