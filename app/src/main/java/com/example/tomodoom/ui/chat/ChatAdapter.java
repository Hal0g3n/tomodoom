package com.example.tomodoom.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tomodoom.databinding.ItemChatBotBinding;
import com.example.tomodoom.databinding.ItemChatSystemBinding;
import com.example.tomodoom.databinding.ItemChatUserBinding;
import com.example.tomodoom.model.ChatMessage;

/**
 * Chat transcript adapter with one view type per {@link ChatMessage.Type}: user bubbles,
 * bot bubbles and centered system/MCP notices.
 */
public final class ChatAdapter extends ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_BOT = 1;
    private static final int VIEW_TYPE_SYSTEM = 2;

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChatMessage>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
                    return oldItem.equals(newItem);
                }
            };

    public ChatAdapter() {
        super(DIFF_CALLBACK);
    }

    @Override
    public int getItemViewType(int position) {
        switch (getItem(position).getType()) {
            case USER:
                return VIEW_TYPE_USER;
            case BOT:
                return VIEW_TYPE_BOT;
            case SYSTEM:
            default:
                return VIEW_TYPE_SYSTEM;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_USER:
                return new UserMessageViewHolder(ItemChatUserBinding.inflate(inflater, parent, false));
            case VIEW_TYPE_BOT:
                return new BotMessageViewHolder(ItemChatBotBinding.inflate(inflater, parent, false));
            case VIEW_TYPE_SYSTEM:
            default:
                return new SystemMessageViewHolder(ItemChatSystemBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** Base holder: each view type binds one {@link ChatMessage}. */
    abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(@NonNull ChatMessage message);
    }

    static final class UserMessageViewHolder extends MessageViewHolder {
        private final TextView text;

        UserMessageViewHolder(@NonNull ItemChatUserBinding binding) {
            super(binding.getRoot());
            text = binding.messageText;
        }

        @Override
        void bind(@NonNull ChatMessage message) {
            text.setText(message.getText());
        }
    }

    static final class BotMessageViewHolder extends MessageViewHolder {
        private final TextView text;

        BotMessageViewHolder(@NonNull ItemChatBotBinding binding) {
            super(binding.getRoot());
            text = binding.messageText;
        }

        @Override
        void bind(@NonNull ChatMessage message) {
            text.setText(message.getText());
        }
    }

    static final class SystemMessageViewHolder extends MessageViewHolder {
        private final TextView text;

        SystemMessageViewHolder(@NonNull ItemChatSystemBinding binding) {
            super(binding.getRoot());
            text = binding.messageText;
        }

        @Override
        void bind(@NonNull ChatMessage message) {
            text.setText(message.getText());
        }
    }
}
