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
 * starts on it) or {@code content="centre"} (only drags starting in the model box, leaving the rest
 * of the viewport to the tab pager). Under {@code centre} the host publishes the box with
 * {@link #touchBoxScript}, and a fast flick inside it still changes tab ({@link #isTabFlick}).</p>
 *
 * <p>The host places the pet with {@link #frameScript}: the page draws it only inside that rect
 * (rounded corners, CSS px) of its transparent canvas, framed as it would be in a viewport of the
 * rect's size. A frame applies only while the page's size is
 * the frame's {@code hostW} x {@code hostH}, so a frame sent just before or after a resize is
 * harmless. {@link #CLEAR_FRAME_SCRIPT} goes back to the whole canvas.</p>
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
    /** {@code content} value meaning "the page keeps slower drags that start in the model box". */
    public static final String TOUCH_CENTRE = "centre";
    /** Global holding the model box as viewport fractions: {@code {left, top, right, bottom}}. */
    public static final String TOUCH_BOX_GLOBAL = "tomodoomTouchBox";
    /** Event dispatched on {@code window} with the frame (or null) as {@code event.detail}. */
    public static final String FRAME_EVENT = "tomodoom:frame";
    /** Global holding the frame {@code {x, y, w, h, r, lift, hostW, hostH}}, or null. */
    public static final String FRAME_GLOBAL = "tomodoomFrame";
    /** Clears the transition frame: the pet fills the whole page again. */
    public static final String CLEAR_FRAME_SCRIPT = "(function(){window." + FRAME_GLOBAL
            + "=null;window.dispatchEvent(new CustomEvent('" + FRAME_EVENT
            + "',{detail:null}));})();";
    /** Horizontal speed from which a drag in the model box is a tab flick. */
    public static final float TAB_FLICK_MIN_DP_PER_SEC = 1200f;
    /** A flick must reach that speed this soon after touching down; later drags stay rotations. */
    public static final long TAB_FLICK_WINDOW_MS = 200L;
    /** A flick's horizontal speed must exceed its vertical speed by this factor. */
    private static final float TAB_FLICK_HORIZONTAL_RATIO = 1.5f;

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

    /** @return true if ({@code x}, {@code y}) is in the box; left/top inclusive, right/bottom not. */
    public static boolean isModelBox(float x, float y,
                                     float left, float top, float right, float bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    /** @return JavaScript publishing the model box to the page, as viewport fractions. */
    @NonNull
    public static String touchBoxScript(float left, float top, float right, float bottom) {
        return String.format(Locale.ROOT,
                "window.%s={left:%.4f,top:%.4f,right:%.4f,bottom:%.4f};",
                TOUCH_BOX_GLOBAL, left, top, right, bottom);
    }

    /**
     * @return JavaScript publishing a frame. {@code x}/{@code y} are the rect's top-left corner
     * inside the page, {@code radius} its corner radius and {@code hostWidth}/{@code hostHeight}
     * the page size the rect was worked out for, all in CSS px. {@code lift} (0-1) is how much to
     * raise the pet clear of a health bar below it: 1 on Pets, 0 on Customise.
     */
    @NonNull
    public static String frameScript(float x, float y, float width, float height, float radius,
                                     float lift, float hostWidth, float hostHeight) {
        return String.format(Locale.ROOT,
                "(function(f){window.%s=f;window.dispatchEvent(new CustomEvent('%s',{detail:f}));})"
                        + "({x:%.2f,y:%.2f,w:%.2f,h:%.2f,r:%.2f,lift:%.3f,hostW:%.2f,hostH:%.2f});",
                FRAME_GLOBAL, FRAME_EVENT, x, y, width, height, radius, lift, hostWidth,
                hostHeight);
    }

    /**
     * @param velocityXDp horizontal finger speed in dp per second (either direction).
     * @param velocityYDp vertical finger speed in dp per second (either direction).
     * @param elapsedMs   time since the finger touched down.
     * @return true if a drag in the model box should change tab instead of rotating the pet.
     */
    public static boolean isTabFlick(float velocityXDp, float velocityYDp, long elapsedMs) {
        float speedX = Math.abs(velocityXDp);
        return elapsedMs <= TAB_FLICK_WINDOW_MS
                && speedX >= TAB_FLICK_MIN_DP_PER_SEC
                && speedX >= Math.abs(velocityYDp) * TAB_FLICK_HORIZONTAL_RATIO;
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
