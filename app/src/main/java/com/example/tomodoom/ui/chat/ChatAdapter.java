package com.example.tomodoom.ui.chat;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.ItemChatBotBinding;
import com.example.tomodoom.databinding.ItemChatCodeBlockBinding;
import com.example.tomodoom.databinding.ItemChatSystemBinding;
import com.example.tomodoom.databinding.ItemChatUserBinding;
import com.example.tomodoom.model.ChatMessage;
import com.example.tomodoom.model.ChatTextScale;

import java.util.List;

/**
 * Chat transcript adapter with one view type per {@link ChatMessage.Type}: user bubbles,
 * bot bubbles and centered system/MCP notices. Bot replies are rendered as Markdown with LaTeX
 * ({@link ChatMarkdown}), with each fenced code block in its own monospace, scrollable, copyable
 * block ({@link MarkdownSegments}). User and system text stays plain.
 *
 * <p>User and bot text (prose, maths and code) follow the chat text size chosen in Settings
 * ({@link #setTextScale}); system notices keep their size.</p>
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

    @NonNull
    private final Context context;
    @NonNull
    private final TextSizes sizes;
    @NonNull
    private ChatMarkdown markdown;

    public ChatAdapter(@NonNull Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
        sizes = new TextSizes(context);
        markdown = new ChatMarkdown(context, sizes.scale);
    }

    /**
     * Shows user and bot text at {@code scale} times the default size. Rebinds every message,
     * since the formulas have to be typeset again at the new size.
     */
    @SuppressLint("NotifyDataSetChanged") // every row changes size
    public void setTextScale(float scale) {
        float snapped = ChatTextScale.nearestStep(scale);
        if (snapped == sizes.scale) {
            return;
        }
        sizes.scale = snapped;
        markdown = new ChatMarkdown(context, snapped);
        notifyDataSetChanged();
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
                return new UserMessageViewHolder(ItemChatUserBinding.inflate(inflater, parent, false),
                        sizes);
            case VIEW_TYPE_BOT:
                return new BotMessageViewHolder(ItemChatBotBinding.inflate(inflater, parent, false),
                        this, sizes);
            case VIEW_TYPE_SYSTEM:
            default:
                return new SystemMessageViewHolder(ItemChatSystemBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** The base text sizes (px) and the current scale, shared by the holders. */
    static final class TextSizes {
        final float bubblePx;
        final float codePx;
        float scale = ChatTextScale.DEFAULT;

        TextSizes(@NonNull Context context) {
            bubblePx = context.getResources().getDimension(R.dimen.chat_bubble_text_size);
            codePx = context.getResources().getDimension(R.dimen.chat_code_text_size);
        }

        void applyBubble(@NonNull TextView view) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, bubblePx * scale);
        }

        void applyCode(@NonNull TextView view) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, codePx * scale);
        }
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
        private final TextSizes sizes;

        UserMessageViewHolder(@NonNull ItemChatUserBinding binding, @NonNull TextSizes sizes) {
            super(binding.getRoot());
            text = binding.messageText;
            this.sizes = sizes;
        }

        @Override
        void bind(@NonNull ChatMessage message) {
            sizes.applyBubble(text);
            text.setText(message.getText());
        }
    }

    static final class BotMessageViewHolder extends MessageViewHolder {
        private final LinearLayout container;
        /** Read at each bind: the adapter swaps its Markdown renderer when the text size changes. */
        private final ChatAdapter adapter;
        private final TextSizes sizes;

        BotMessageViewHolder(@NonNull ItemChatBotBinding binding, @NonNull ChatAdapter adapter,
                             @NonNull TextSizes sizes) {
            super(binding.getRoot());
            container = binding.messageContainer;
            this.adapter = adapter;
            this.sizes = sizes;
        }

        @Override
        void bind(@NonNull ChatMessage message) {
            List<MarkdownSegments.Segment> segments = MarkdownSegments.split(message.getText());
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            for (int k = 0; k < segments.size(); k++) {
                MarkdownSegments.Segment segment = segments.get(k);
                // Reuse the recycled child at this position when it's the same kind.
                View child = k < container.getChildCount() ? container.getChildAt(k) : null;
                boolean reusable = child != null && Boolean.valueOf(segment.isCode()).equals(child.getTag());
                if (!reusable) {
                    if (child != null) {
                        container.removeViews(k, container.getChildCount() - k);
                    }
                    child = segment.isCode()
                            ? ItemChatCodeBlockBinding.inflate(inflater, container, false).getRoot()
                            : inflater.inflate(R.layout.item_chat_bot_prose, container, false);
                    child.setTag(segment.isCode());
                    container.addView(child);
                }
                if (segment.isCode()) {
                    ItemChatCodeBlockBinding code = ItemChatCodeBlockBinding.bind(child);
                    sizes.applyCode(code.codeText);
                    bindCode(code, segment);
                } else {
                    sizes.applyBubble((TextView) child);
                    adapter.markdown.show((TextView) child, segment.getText());
                }
            }
            if (container.getChildCount() > segments.size()) {
                container.removeViews(segments.size(), container.getChildCount() - segments.size());
            }
        }

        private static void bindCode(@NonNull ItemChatCodeBlockBinding code,
                                     @NonNull MarkdownSegments.Segment segment) {
            Context context = code.getRoot().getContext();
            String language = segment.getLanguage() != null
                    ? segment.getLanguage()
                    : context.getString(R.string.chat_code_language_default);
            code.codeLanguage.setText(language);
            code.codeText.setText(segment.getText());
            code.codeScroll.scrollTo(0, 0);
            code.codeCopy.setContentDescription(
                    context.getString(R.string.chat_code_copy_description, language));
            String text = segment.getText();
            code.codeCopy.setOnClickListener(v -> copyToClipboard(v.getContext(), text));
        }

        private static void copyToClipboard(@NonNull Context context, @NonNull String text) {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    context.getString(R.string.chat_code_clip_label), text));
            // Android 13+ shows its own confirmation for clipboard writes.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.chat_code_copied, Toast.LENGTH_SHORT).show();
            }
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
