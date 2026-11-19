package com.example.tomodoom.contracts;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.example.tomodoom.model.McpToolResult;

import java.util.List;
import java.util.Map;

/**
 * Contract for the <b>Chat/MCP Agent</b>: the Model Context Protocol (MCP) tool bridge that
 * lets the chatbot act on the pet. It is the <b>only</b> path by which the chat subsystem may
 * change pet state.
 *
 * <h3>Tools</h3>
 * <table>
 *     <tr><th>Name</th><th>Params</th><th>Effect</th></tr>
 *     <tr><td>{@link #TOOL_AWARD_HP}</td><td>{@code amount} (Number, required, &gt; 0),
 *         {@code reason} (String, optional)</td><td>increase HP</td></tr>
 *     <tr><td>{@link #TOOL_EQUIP_ITEM}</td><td>{@code slot} (String, required, see
 *         {@code AccessorySlot}), {@code itemId} (String, optional; absent/empty = unequip)</td>
 *         <td>equip or unequip</td></tr>
 *     <tr><td>{@link #TOOL_GET_PET_STATE}</td><td>none</td><td>read-only snapshot</td></tr>
 * </table>
 *
 * <h3>Contract</h3>
 * <ul>
 *     <li><b>Thread-safe</b>; callable from any thread (typically the chat service's worker).</li>
 *     <li><b>Never throws</b> for bad input: returns {@link McpToolResult#error(String)}.</li>
 *     <li>{@link #onQuizPassed} is <b>idempotent per quizId</b>: replays are rejected so a buggy
 *     or malicious backend cannot farm HP.</li>
 *     <li>{@link McpToolResult#getMessage()} is user-facing and is shown as a SYSTEM chat message.</li>
 *     <li>Successful results include {@code hp} and {@code maxHp} in {@link McpToolResult#getData()}.</li>
 * </ul>
 *
 * <p>Default implementation: {@code stubs.DefaultMcpPetActionHandler}, which is production-capable
 * and wired to {@code PetStateRepository}.</p>
 */
public interface McpPetActionHandler {

    String TOOL_AWARD_HP = "award_hp";
    String TOOL_EQUIP_ITEM = "equip_item";
    String TOOL_GET_PET_STATE = "get_pet_state";

    /**
     * Awards HP for a quiz the backend verified as passed.
     *
     * @param quizId   the passed quiz's ID; a second call with the same ID is rejected.
     * @param hpReward HP to award (&gt; 0); implementations may cap it.
     * @return success with the new HP in {@code data}, or an error (duplicate, invalid reward).
     */
    @AnyThread
    @NonNull
    McpToolResult onQuizPassed(@NonNull String quizId, int hpReward);

    /**
     * Executes a named MCP tool.
     *
     * @param toolName one of {@link #getSupportedToolNames()}.
     * @param params   JSON-style arguments; numbers may arrive as any {@link Number} or a
     *                 numeric String.
     * @return never null; an error result for unknown tools or invalid params.
     */
    @AnyThread
    @NonNull
    McpToolResult executeMcpTool(@NonNull String toolName, @NonNull Map<String, Object> params);

    /** @return the tool names this handler accepts, for advertising to the MCP client. */
    @AnyThread
    @NonNull
    List<String> getSupportedToolNames();
}
