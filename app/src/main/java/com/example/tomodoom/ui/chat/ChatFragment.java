package com.example.tomodoom.ui.chat;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.tomodoom.R;
import com.example.tomodoom.databinding.FragmentChatBinding;
import com.example.tomodoom.di.ViewModelFactory;
import com.example.tomodoom.model.ChatMessage;
import com.example.tomodoom.viewmodel.ChatViewModel;

import java.util.List;

/**
 * Chat page: the chat transcript (user, bot and system/MCP messages) plus an input bar.
 * While a quiz is active, the input hint asks for an answer.
 */
public class ChatFragment extends Fragment {

    private FragmentChatBinding binding;
    private ChatViewModel viewModel;
    private ChatAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(requireContext()))
                .get(ChatViewModel.class);

        adapter = new ChatAdapter(requireContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.chatList.setLayoutManager(layoutManager);
        binding.chatList.setAdapter(adapter);

        viewModel.getMessages().observe(getViewLifecycleOwner(), this::showMessages);
        viewModel.getActiveQuiz().observe(getViewLifecycleOwner(), quiz ->
                binding.messageInput.setHint(quiz != null
                        ? R.string.chat_input_hint_quiz
                        : R.string.chat_input_hint));

        binding.sendButton.setOnClickListener(v -> sendCurrentInput());
        binding.messageInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeSend = actionId == EditorInfo.IME_ACTION_SEND;
            boolean enterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (imeSend || enterKey) {
                sendCurrentInput();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void showMessages(@NonNull List<ChatMessage> messages) {
        adapter.submitList(messages, () -> {
            if (binding != null && !messages.isEmpty()) {
                binding.chatList.scrollToPosition(messages.size() - 1);
            }
        });
    }

    private void sendCurrentInput() {
        CharSequence input = binding.messageInput.getText();
        String text = input != null ? input.toString().trim() : "";
        if (text.isEmpty()) {
            return;
        }
        viewModel.sendMessage(text);
        binding.messageInput.setText("");
    }
}
