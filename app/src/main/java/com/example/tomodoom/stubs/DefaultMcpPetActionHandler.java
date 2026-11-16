package com.example.tomodoom.stubs;

import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.contracts.McpPetActionHandler;
import com.example.tomodoom.model.AccessoryCatalog;
import com.example.tomodoom.model.AccessoryItem;
import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.McpToolResult;
import com.example.tomodoom.model.PetState;
import com.example.tomodoom.repository.PetStateRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default, <b>production-capable</b> MCP pet tool bridge wired directly to
 * {@link PetStateRepository}. Passing a quiz immediately raises HP, and the Pets UI updates
 * through LiveData.
 *
 * <p>Safeguards against a misbehaving backend:</p>
 * <ul>
 *     <li>{@link #onQuizPassed} rewards each {@code quizId} at most once per process. Real backends
 *     must therefore use globally unique quiz IDs (e.g. UUIDs).</li>
 *     <li>Any single award is capped at {@link #MAX_HP_PER_AWARD}.</li>
 *     <li>{@code equip_item} only accepts slots and items from {@link AccessorySlot}/{@link AccessoryCatalog}.</li>
 * </ul>
 *
 * <p>The TruFar/MCP Agent may extend the tool set here or replace this class in
 * {@code AppContainer}.</p>
 */
public final class DefaultMcpPetActionHandler implements McpPetActionHandler {

    private static final String TAG = "DefaultMcpHandler";

    /** Upper bound on HP granted by a single award or {@code award_hp} call. */
    public static final int MAX_HP_PER_AWARD = 50;

    private static final List<String> SUPPORTED_TOOLS = Collections.unmodifiableList(
            Arrays.asList(TOOL_AWARD_HP, TOOL_EQUIP_ITEM, TOOL_GET_PET_STATE));

    @NonNull
    private final PetStateRepository petStateRepository;
    /** Guarded by itself. */
    private final Set<String> rewardedQuizIds = new HashSet<>();

    public DefaultMcpPetActionHandler(@NonNull PetStateRepository petStateRepository) {
        this.petStateRepository = petStateRepository;
    }

    @AnyThread
    @NonNull
    @Override
    public McpToolResult onQuizPassed(@NonNull String quizId, int hpReward) {
        if (quizId.isEmpty()) {
            return McpToolResult.error("Quiz reward rejected: missing quiz id.");
        }
        if (hpReward <= 0) {
            return McpToolResult.error("Quiz reward rejected: invalid HP reward " + hpReward + ".");
        }
        synchronized (rewardedQuizIds) {
            if (!rewardedQuizIds.add(quizId)) {
                return McpToolResult.error("Quiz " + quizId + " was already rewarded.");
            }
        }
        int amount = Math.min(hpReward, MAX_HP_PER_AWARD);
        PetState after = petStateRepository.increaseHp(amount, "quiz_passed:" + quizId);
        Map<String, Object> data = stateData(after);
        data.put("quizId", quizId);
        data.put("awarded", amount);
        return McpToolResult.success(awardMessage(amount, after), data);
    }

    @AnyThread
    @NonNull
    @Override
    public McpToolResult executeMcpTool(@NonNull String toolName, @NonNull Map<String, Object> params) {
        try {
            switch (toolName) {
                case TOOL_AWARD_HP:
                    return awardHp(params);
                case TOOL_EQUIP_ITEM:
                    return equipItem(params);
                case TOOL_GET_PET_STATE:
                    return getPetState();
                default:
                    return McpToolResult.error("Unknown MCP tool \"" + toolName + "\". Supported: "
                            + SUPPORTED_TOOLS + ".");
            }
        } catch (RuntimeException e) {
            // Contract: never throw for bad input.
            Log.w(TAG, "MCP tool " + toolName + " failed", e);
            return McpToolResult.error("MCP tool \"" + toolName + "\" failed: " + e.getMessage());
        }
    }

    @AnyThread
    @NonNull
    @Override
    public List<String> getSupportedToolNames() {
        return SUPPORTED_TOOLS;
    }

    // ---------------------------------------------------------------------------------------

    @NonNull
    private McpToolResult awardHp(@NonNull Map<String, Object> params) {
        Integer amount = asInt(params.get("amount"));
        if (amount == null || amount <= 0) {
            return McpToolResult.error(TOOL_AWARD_HP + " requires a positive numeric \"amount\".");
        }
        int capped = Math.min(amount, MAX_HP_PER_AWARD);
        String reason = asString(params.get("reason"));
        PetState after = petStateRepository.increaseHp(capped,
                "mcp:" + TOOL_AWARD_HP + (reason != null ? ":" + reason : ""));
        Map<String, Object> data = stateData(after);
        data.put("awarded", capped);
        return McpToolResult.success(awardMessage(capped, after), data);
    }

    @NonNull
    private McpToolResult equipItem(@NonNull Map<String, Object> params) {
        String slot = asString(params.get("slot"));
        if (!AccessorySlot.isValid(slot)) {
            return McpToolResult.error(TOOL_EQUIP_ITEM + ": unknown slot \"" + slot + "\". Expected one of "
                    + AccessorySlot.ALL + ".");
        }
        String itemId = asString(params.get("itemId"));
        if (itemId == null) {
            PetState after = petStateRepository.equipItem(slot, null);
            return McpToolResult.success("Unequipped " + slot + " via MCP.", stateData(after));
        }
        AccessoryItem item = AccessoryCatalog.findById(itemId);
        if (item == null || !item.getSlot().equals(slot)) {
            return McpToolResult.error(TOOL_EQUIP_ITEM + ": unknown item \"" + itemId + "\" for slot "
                    + slot + ".");
        }
        PetState after = petStateRepository.equipItem(slot, itemId);
        return McpToolResult.success("Equipped " + item.getDisplayName() + " (" + slot + ") via MCP.",
                stateData(after));
    }

    @NonNull
    private McpToolResult getPetState() {
        PetState state = petStateRepository.getCurrentState();
        return McpToolResult.success(state.getPetName() + " has " + state.getCurrentHp() + "/"
                + state.getMaxHp() + " HP.", stateData(state));
    }

    @NonNull
    private static String awardMessage(int amount, @NonNull PetState after) {
        return "+" + amount + " HP awarded via MCP! (" + after.getCurrentHp() + "/" + after.getMaxHp() + " HP)";
    }

    @NonNull
    private static Map<String, Object> stateData(@NonNull PetState state) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("petName", state.getPetName());
        data.put("hp", state.getCurrentHp());
        data.put("maxHp", state.getMaxHp());
        data.put("equipped", new LinkedHashMap<String, Object>(state.getEquippedItems()));
        return data;
    }

    @Nullable
    private static Integer asInt(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** @return the trimmed string, or null if absent/blank/not a string. */
    @Nullable
    private static String asString(@Nullable Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String s = ((String) value).trim();
        return s.isEmpty() ? null : s;
    }
}
