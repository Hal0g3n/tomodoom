package com.example.tomodoom.maniengine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.tomodoom.model.AccessoryCatalog;
import com.example.tomodoom.model.AccessoryItem;
import com.example.tomodoom.model.PetMood;
import com.example.tomodoom.model.PetState;

import java.util.Locale;
import java.util.Map;

/**
 * The app ↔ page contract for the Maniengine HTML export (CLAUDE.md §5.1). Pure Java, so the
 * exact JSON and script the page receives are unit-tested.
 *
 * <p>On every pet-state change the host runs {@link #deliveryScript}, which</p>
 * <ol>
 *     <li>sets {@code window.tomodoomPetState} to the state object (for code that loads late),</li>
 *     <li>dispatches {@code CustomEvent("tomodoom:petstate", {detail: state})} on {@code window}.</li>
 * </ol>
 * <p>State object (see {@link #stateJson}):</p>
 * <pre>
 * { "name": "Tomo", "hp": 50, "maxHp": 100, "hpFraction": 0.5, "mood": "okay", "fainted": false,
 *   "equipped": { "hat": { "id": "hat_cap", "name": "Cap" } } }
 * </pre>
 * <p>{@code mood} is a {@link PetMood#getId()}. {@code equipped} holds only filled slots, keyed by
 * {@code AccessorySlot} id; {@code name} falls back to the item id if it isn't in the catalog.</p>
 *
 * <p>The page opts in to keeping horizontal drags (e.g. orbit controls), instead of letting them
 * swipe between tabs, with {@code <meta name="tomodoom-touch" content="capture">} (every drag that
 * starts on it) or {@code content="centre"} (only drags starting in {@link #isCentreZone}, leaving
 * the side edges and bottom strip to the tab pager).</p>
 */
public final class ManiengineProtocol {

    /** Event dispatched on {@code window} with the state as {@code event.detail}. */
    public static final String STATE_EVENT = "tomodoom:petstate";
    /** Global holding the latest state, for code that starts after the first event. */
    public static final String STATE_GLOBAL = "tomodoomPetState";
    /** {@code <meta name>} the page uses to declare its touch policy. */
    public static final String TOUCH_META_NAME = "tomodoom-touch";
    /** {@code content} value meaning "the page keeps all drags that start on it". */
    public static final String TOUCH_CAPTURE = "capture";
    /** {@code content} value meaning "the page keeps drags that start in the centre zone". */
    public static final String TOUCH_CENTRE = "centre";
    /** Width of each side strip left to the tab pager, as a fraction of the viewport width. */
    public static final double TAB_SWIPE_EDGE_FRACTION = 0.15;
    /** Height of the bottom strip left to the tab pager, as a fraction of the viewport height. */
    public static final double TAB_SWIPE_BOTTOM_FRACTION = 0.20;

    /** Evaluates to the page's {@link #TOUCH_META_NAME} content, or {@code ""}. */
    public static final String TOUCH_POLICY_QUERY =
            "(function(){var m=document.querySelector('meta[name=\"" + TOUCH_META_NAME + "\"]');"
                    + "return m?String(m.content):'';})()";

    private ManiengineProtocol() {
    }

    /** @return the state object as compact JSON (stable field order; equal states → equal JSON). */
    @NonNull
    public static String stateJson(@NonNull PetState state) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"name\":");
        appendString(sb, state.getPetName());
        sb.append(",\"hp\":").append(state.getCurrentHp());
        sb.append(",\"maxHp\":").append(state.getMaxHp());
        sb.append(",\"hpFraction\":").append(state.getHpFraction());
        sb.append(",\"mood\":");
        appendString(sb, PetMood.of(state).getId());
        sb.append(",\"fainted\":").append(state.isFainted());
        sb.append(",\"equipped\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : state.getEquippedItems().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            AccessoryItem item = AccessoryCatalog.findById(e.getValue());
            appendString(sb, e.getKey());
            sb.append(":{\"id\":");
            appendString(sb, e.getValue());
            sb.append(",\"name\":");
            appendString(sb, item != null ? item.getDisplayName() : e.getValue());
            sb.append('}');
        }
        sb.append("}}");
        return sb.toString();
    }

    /** @return JavaScript that publishes {@code stateJson} to the page (see the class docs). */
    @NonNull
    public static String deliveryScript(@NonNull String stateJson) {
        return "(function(s){window." + STATE_GLOBAL + "=s;"
                + "window.dispatchEvent(new CustomEvent('" + STATE_EVENT + "',{detail:s}));})("
                + stateJson + ");";
    }

    /**
     * @param evaluateResult the raw {@code evaluateJavascript} result of {@link #TOUCH_POLICY_QUERY}
     *                       (a JSON-encoded string such as {@code "\"capture\""}, or {@code "null"}).
     * @return true if the page asked to capture drags.
     */
    public static boolean capturesTouch(@Nullable String evaluateResult) {
        return TOUCH_CAPTURE.equals(touchPolicy(evaluateResult));
    }

    /**
     * @param evaluateResult the raw {@code evaluateJavascript} result of {@link #TOUCH_POLICY_QUERY}.
     * @return the page's touch policy in lower case, or {@code ""} if it declared none.
     */
    @NonNull
    public static String touchPolicy(@Nullable String evaluateResult) {
        if (evaluateResult == null) {
            return "";
        }
        String v = evaluateResult.trim();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            v = v.substring(1, v.length() - 1).trim();
        }
        return v.toLowerCase(Locale.ROOT);
    }

    /**
     * @return true if a touch at ({@code x}, {@code y}) in a {@code width} × {@code height} viewport
     * belongs to the page under {@link #TOUCH_CENTRE}. {@code index.html} mirrors this test.
     */
    public static boolean isCentreZone(float x, float y, int width, int height) {
        return x >= width * TAB_SWIPE_EDGE_FRACTION
                && x < width * (1 - TAB_SWIPE_EDGE_FRACTION)
                && y < height * (1 - TAB_SWIPE_BOTTOM_FRACTION);
    }

    /** JSON string literal; also escapes U+2028/2029 and '<' so the JSON is safe inline in JS. */
    static void appendString(@NonNull StringBuilder sb, @NonNull String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ' || c == '<') {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
