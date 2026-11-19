package com.example.tomodoom.viewmodel;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.tomodoom.contracts.DoomScrollListener;
import com.example.tomodoom.contracts.DoomScrollMonitor;
import com.example.tomodoom.contracts.McpPetActionHandler;
import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.DoomScrollEvent;
import com.example.tomodoom.model.McpToolResult;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.ChatRepository;
import com.example.tomodoom.repository.PetStateRepository;
import com.example.tomodoom.repository.StatsRepository;
import com.example.tomodoom.stats.DemoStats;

import java.util.TimeZone;
import java.util.UUID;

/**
 * ViewModel for the hidden debug panel (long-press the Pets tab). Every action goes through the
 * same path the real feature uses, so the panel tests the app rather than bypassing it: HP through
 * {@link PetStateRepository} (reason {@value #REASON}), doom-scrolling through the monitor's
 * listener, quiz awards through the {@link McpPetActionHandler} (replay guard and cap included).
 */
public class DebugViewModel extends ViewModel {

    /** Reason tag for HP changes made from the panel (CLAUDE.md §3.1). */
    static final String REASON = "debug";
    /** Source package of synthetic doom-scroll events. */
    static final String DEBUG_PACKAGE = "debug.tomodoom";
    /** HP a simulated quiz pass awards, the same as a correct answer on Gemini. */
    public static final int QUIZ_REWARD_HP = 10;
    /** One counted minute, as the real monitor charges it. */
    private static final long ONE_MINUTE_MS = 60_000L;
    /** Session length of a simulated warning (the real monitor warns every 10 minutes). */
    private static final long WARNING_SESSION_MS = 10 * ONE_MINUTE_MS;

    @NonNull
    private final PetStateRepository petStateRepository;
    @NonNull
    private final McpPetActionHandler mcpHandler;
    @NonNull
    private final ChatRepository chatRepository;
    @NonNull
    private final DoomScrollMonitor doomScrollMonitor;
    @NonNull
    private final DoomScrollListener doomScrollListener;
    @NonNull
    private final StatsRepository statsRepository;

    private final MutableLiveData<String> lastResult = new MutableLiveData<>(null);
    @Nullable
    private String lastQuizId; // main thread only

    public DebugViewModel(@NonNull PetStateRepository petStateRepository,
                          @NonNull McpPetActionHandler mcpHandler,
                          @NonNull ChatRepository chatRepository,
                          @NonNull DoomScrollMonitor doomScrollMonitor,
                          @NonNull DoomScrollListener doomScrollListener,
                          @NonNull StatsRepository statsRepository) {
        this.petStateRepository = petStateRepository;
        this.mcpHandler = mcpHandler;
        this.chatRepository = chatRepository;
        this.doomScrollMonitor = doomScrollMonitor;
        this.doomScrollListener = doomScrollListener;
        this.statsRepository = statsRepository;
    }

    @NonNull
    public LiveData<PetState> getPetState() {
        return petStateRepository.getPetState();
    }

    /** The message of the last MCP result (quiz award or replay), or {@code null}. */
    @NonNull
    public LiveData<String> getLastResult() {
        return lastResult;
    }

    // --- Pet ---------------------------------------------------------------------------------

    /** Adds {@code delta} HP (negative removes it); clamped by the repository. */
    @MainThread
    public void changeHp(int delta) {
        if (delta > 0) {
            petStateRepository.increaseHp(delta, REASON);
        } else if (delta < 0) {
            petStateRepository.decreaseHp(-delta, REASON);
        }
    }

    /** Sets HP to {@code targetHp} (clamped to {@code [0, maxHp]}). */
    @MainThread
    public void setHp(int targetHp) {
        PetState state = petStateRepository.getCurrentState();
        changeHp(hpDeltaTo(state.getCurrentHp(), targetHp, state.getMaxHp()));
    }

    /** Sets HP to the pet's maximum. */
    @MainThread
    public void setFullHp() {
        setHp(petStateRepository.getCurrentState().getMaxHp());
    }

    @MainThread
    public void unequipAll() {
        for (String slot : AccessorySlot.ALL) {
            petStateRepository.equipItem(slot, null);
        }
    }

    /**
     * The HP change that moves {@code currentHp} to {@code targetHp}, with the target clamped to
     * {@code [0, maxHp]}. Pure, so it's JVM-testable.
     */
    static int hpDeltaTo(int currentHp, int targetHp, int maxHp) {
        int clamped = Math.max(0, Math.min(targetHp, maxHp));
        return clamped - currentHp;
    }

    // --- Doom scroll -------------------------------------------------------------------------

    public boolean isUsageAccessGranted() {
        return doomScrollMonitor.isPermissionGranted();
    }

    public boolean isMonitoring() {
        return doomScrollMonitor.isMonitoring();
    }

    /** One counted minute of doom-scrolling: −1 HP, like the real monitor. */
    @MainThread
    public void simulateDoomScrollMinute() {
        doomScrollListener.onDoomScrollDetected(
                DoomScrollEvent.now(DEBUG_PACKAGE, ONE_MINUTE_MS, 1));
    }

    /** The 10-minute warning, which posts the real "Time to look up" notification. */
    @MainThread
    public void simulateDoomScrollWarning() {
        doomScrollListener.onDoomScrollWarning(DEBUG_PACKAGE, WARNING_SESSION_MS);
    }

    // --- Chat / MCP --------------------------------------------------------------------------

    /** Awards {@link #QUIZ_REWARD_HP} for a new, unique quiz ID, as a graded chat answer would. */
    @MainThread
    public void simulateQuizPass() {
        lastQuizId = "debug:" + UUID.randomUUID();
        report(mcpHandler.onQuizPassed(lastQuizId, QUIZ_REWARD_HP));
    }

    /** Re-submits the last simulated quiz, which the replay guard should reject. */
    @MainThread
    public void replayLastQuiz() {
        if (lastQuizId == null) {
            simulateQuizPass();
            return;
        }
        report(mcpHandler.onQuizPassed(lastQuizId, QUIZ_REWARD_HP));
    }

    /** What 10 minutes in the background does: clears the transcript and greets again. */
    @MainThread
    public void startNewConversation() {
        chatRepository.startNewConversation();
    }

    // --- Stats -------------------------------------------------------------------------------

    /**
     * Replaces the Stats history with a fresh, random week of demo data ending at the pet's
     * current HP ({@link DemoStats}). The pet itself is untouched.
     */
    @MainThread
    public void fillDemoStats() {
        PetState pet = petStateRepository.getCurrentState();
        statsRepository.replaceAll(DemoStats.generate(System.currentTimeMillis(),
                TimeZone.getDefault(), pet.getCurrentHp(), pet.getMaxHp(), System.nanoTime()));
    }

    /** Forgets the Stats history (demo or real); tracking starts again now. */
    @MainThread
    public void clearStats() {
        statsRepository.clear(petStateRepository.getCurrentState());
    }

    private void report(@NonNull McpToolResult result) {
        lastResult.postValue((result.isSuccess() ? "" : "⚠ ") + result.getMessage());
    }
}
