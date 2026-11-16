package com.example.tomodoom.stubs;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.tomodoom.contracts.McpPetActionHandler;
import com.example.tomodoom.contracts.TruFarChatService;
import com.example.tomodoom.model.McpToolResult;
import com.example.tomodoom.model.QuizEvaluation;
import com.example.tomodoom.model.QuizPrompt;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local stand-in for the TruFar backend, so the chat UI and the quiz → MCP → HP loop can be
 * tested without a network.
 *
 * <h3>Behaviour</h3>
 * <ul>
 *     <li>On {@link #connect()} it sends a greeting.</li>
 *     <li>{@code quiz} (any message containing it) issues a sample {@link QuizPrompt} worth
 *     {@value #QUIZ_HP_REWARD} HP. Answers are checked case-insensitively.</li>
 *     <li>{@code /tool <name> [key=value ...]} makes the "backend" invoke an MCP tool via
 *     {@link Listener#onToolCallRequested}, e.g. {@code /tool award_hp amount=5}.</li>
 *     <li>{@code help} lists the commands; anything else is echoed.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * All work and <b>all listener callbacks</b> run on one private background thread, after a
 * short simulated latency. That deliberately exercises the "callbacks on any thread" contract.
 * Mutable fields other than {@link #listener} are confined to that thread.
 */
public final class MockTruFarChatService implements TruFarChatService {

    private static final String TAG = "MockTruFarChatService";
    private static final long REPLY_DELAY_MS = 400L;
    static final int QUIZ_HP_REWARD = 10;

    private static final class MockQuiz {
        @NonNull
        final String question;
        @NonNull
        final String displayAnswer;
        @NonNull
        final String[] acceptedAnswers;

        MockQuiz(@NonNull String question, @NonNull String displayAnswer, @NonNull String... acceptedAnswers) {
            this.question = question;
            this.displayAnswer = displayAnswer;
            this.acceptedAnswers = acceptedAnswers;
        }

        boolean accepts(@NonNull String answer) {
            String normalized = answer.trim().toLowerCase(Locale.ROOT);
            for (String accepted : acceptedAnswers) {
                if (accepted.equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final MockQuiz[] QUIZZES = {
            new MockQuiz("What is 2 + 2?", "4", "4", "four"),
            new MockQuiz("How many minutes are in an hour?", "60", "60", "sixty"),
            new MockQuiz("Which colour do you get by mixing blue and yellow?", "green", "green"),
    };

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-trufar");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean connected = new AtomicBoolean(false);
    @Nullable
    private volatile Listener listener;

    // Confined to the worker thread.
    private int quizCounter = 0;
    private final Map<String, MockQuiz> pendingQuizzes = new HashMap<>();

    @AnyThread
    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @AnyThread
    @Override
    public void connect() {
        if (connected.compareAndSet(false, true)) {
            runLater(() -> {
                Listener l = listener;
                if (l != null) {
                    l.onBotMessage("Hi! I'm TruFar (mock). Type \"quiz\" to earn HP for your pet, "
                            + "or \"help\" for more commands.");
                }
            });
        }
    }

    @AnyThread
    @Override
    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            runLater(pendingQuizzes::clear);
        }
    }

    @AnyThread
    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @AnyThread
    @Override
    public void sendMessage(@NonNull String text) {
        if (!ensureConnected()) {
            return;
        }
        runLater(() -> handleMessage(text));
    }

    @AnyThread
    @Override
    public void submitQuizAnswer(@NonNull String quizId, @NonNull String answer) {
        if (!ensureConnected()) {
            return;
        }
        runLater(() -> handleAnswer(quizId, answer));
    }

    // ---------------------------------------------------------------------------------------
    // Worker-thread logic
    // ---------------------------------------------------------------------------------------

    @WorkerThread
    private void handleMessage(@NonNull String text) {
        Listener l = listener;
        if (l == null) {
            return;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("/tool")) {
            handleToolCommand(l, text.trim());
        } else if (lower.equals("help") || lower.equals("/help")) {
            l.onBotMessage("Mock TruFar commands:\n"
                    + "• quiz: answer a question to earn " + QUIZ_HP_REWARD + " HP\n"
                    + "• /tool get_pet_state\n"
                    + "• /tool award_hp amount=5\n"
                    + "• /tool equip_item slot=hat itemId=hat_crown\n"
                    + "• anything else is echoed back");
        } else if (lower.contains("quiz")) {
            issueQuiz(l);
        } else {
            l.onBotMessage("You said: \"" + text + "\". (Mock bot: type \"quiz\" to earn HP.)");
        }
    }

    @WorkerThread
    private void issueQuiz(@NonNull Listener l) {
        quizCounter++;
        MockQuiz quiz = QUIZZES[(quizCounter - 1) % QUIZZES.length];
        String quizId = "mock-quiz-" + quizCounter;
        pendingQuizzes.put(quizId, quiz);
        l.onQuizPrompt(new QuizPrompt(quizId, quiz.question, null, QUIZ_HP_REWARD));
    }

    @WorkerThread
    private void handleAnswer(@NonNull String quizId, @NonNull String answer) {
        Listener l = listener;
        if (l == null) {
            return;
        }
        MockQuiz quiz = pendingQuizzes.remove(quizId);
        if (quiz == null) {
            l.onQuizEvaluated(new QuizEvaluation(quizId, false, 0,
                    "That quiz has expired. Type \"quiz\" for a new one."));
        } else if (quiz.accepts(answer)) {
            l.onQuizEvaluated(new QuizEvaluation(quizId, true, QUIZ_HP_REWARD,
                    "Correct! 🎉 Nice focus."));
        } else {
            l.onQuizEvaluated(new QuizEvaluation(quizId, false, 0,
                    "Not quite. The answer was " + quiz.displayAnswer
                            + ". Type \"quiz\" to try again."));
        }
    }

    /** Parses {@code /tool <name> [key=value ...]}; integer-looking values become Integers. */
    @WorkerThread
    private void handleToolCommand(@NonNull Listener l, @NonNull String command) {
        String[] tokens = command.split("\\s+");
        if (tokens.length < 2) {
            l.onBotMessage("Usage: /tool <name> [key=value ...]. Tools: "
                    + McpPetActionHandler.TOOL_AWARD_HP + ", "
                    + McpPetActionHandler.TOOL_EQUIP_ITEM + ", "
                    + McpPetActionHandler.TOOL_GET_PET_STATE);
            return;
        }
        String toolName = tokens[1];
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 2; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = tokens[i].substring(0, eq);
            String value = tokens[i].substring(eq + 1);
            params.put(key, value.matches("-?\\d{1,9}") ? (Object) Integer.valueOf(value) : value);
        }
        McpToolResult result = l.onToolCallRequested(toolName, params);
        l.onBotMessage("(mock backend received tool result: success=" + result.isSuccess()
                + ", data=" + result.getData() + ")");
    }

    // ---------------------------------------------------------------------------------------

    private boolean ensureConnected() {
        if (connected.get()) {
            return true;
        }
        runLater(() -> {
            Listener l = listener;
            if (l != null) {
                l.onError(new IllegalStateException("TruFar session is not connected"));
            }
        });
        return false;
    }

    private void runLater(@NonNull Runnable task) {
        worker.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                // Exceptions in scheduled tasks are otherwise swallowed silently.
                Log.e(TAG, "mock TruFar task failed", e);
            }
        }, REPLY_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}
