package com.example.tomodoom.maniengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.tomodoom.model.AccessorySlot;
import com.example.tomodoom.model.PetState;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ManiengineProtocolTest {

    @Test
    public void stateJson_defaultPet() {
        assertEquals("{\"name\":\"Tomo\",\"hp\":50,\"maxHp\":100,\"hpFraction\":0.5,"
                        + "\"mood\":\"okay\",\"fainted\":false,\"equipped\":{}}",
                ManiengineProtocol.stateJson(PetState.createDefault()));
    }

    @Test
    public void stateJson_equippedItemsCarryIdAndCatalogName() throws Exception {
        PetState state = PetState.createDefault()
                .withEquippedItem(AccessorySlot.HAT, "hat_cap")
                .withEquippedItem(AccessorySlot.ACCESSORY, "not_in_catalog");
        JSONObject equipped = new JSONObject(ManiengineProtocol.stateJson(state))
                .getJSONObject("equipped");
        assertEquals("hat_cap", equipped.getJSONObject("hat").getString("id"));
        assertEquals("Cap", equipped.getJSONObject("hat").getString("name"));
        assertEquals("not_in_catalog", equipped.getJSONObject("accessory").getString("name"));
    }

    @Test
    public void stateJson_faintedPet() throws Exception {
        JSONObject json = new JSONObject(
                ManiengineProtocol.stateJson(PetState.createDefault().withHp(0)));
        assertTrue(json.getBoolean("fainted"));
        assertEquals("fainted", json.getString("mood"));
    }

    @Test
    public void stateJson_escapesNameSoItStaysValidInlineJs() throws Exception {
        String name = "a\"b\\c\n</script>\u2028";
        String json = ManiengineProtocol.stateJson(new PetState(name, 1, 2, null));
        assertFalse(json.contains("</script>"));
        assertFalse(json.contains("\u2028"));
        assertEquals(name, new JSONObject(json).getString("name"));
    }

    @Test
    public void stateJson_equalStatesGiveEqualJson() {
        PetState a = PetState.createDefault().withEquippedItem(AccessorySlot.HAT, "hat_cap");
        PetState b = PetState.createDefault().withEquippedItem(AccessorySlot.HAT, "hat_cap");
        assertEquals(ManiengineProtocol.stateJson(a), ManiengineProtocol.stateJson(b));
    }

    @Test
    public void deliveryScript_setsGlobalAndDispatchesEvent() {
        String script = ManiengineProtocol.deliveryScript("{\"hp\":1}");
        assertTrue(script.contains("window." + ManiengineProtocol.STATE_GLOBAL + "=s"));
        assertTrue(script.contains("new CustomEvent('" + ManiengineProtocol.STATE_EVENT + "'"));
        assertTrue(script.endsWith("({\"hp\":1});"));
    }

    @Test
    public void capturesTouch_parsesEvaluateJavascriptResults() {
        assertTrue(ManiengineProtocol.capturesTouch("\"capture\""));
        assertTrue(ManiengineProtocol.capturesTouch(" \"Capture\" "));
        assertFalse(ManiengineProtocol.capturesTouch("\"\""));
        assertFalse(ManiengineProtocol.capturesTouch("null"));
        assertFalse(ManiengineProtocol.capturesTouch(null));
        assertFalse(ManiengineProtocol.capturesTouch("\"pass\""));
    }

    @Test
    public void touchPolicy_normalisesEvaluateJavascriptResults() {
        assertEquals("centre", ManiengineProtocol.touchPolicy(" \"Centre\" "));
        assertEquals("capture", ManiengineProtocol.touchPolicy("\"capture\""));
        assertEquals("", ManiengineProtocol.touchPolicy("\"\""));
        assertEquals("", ManiengineProtocol.touchPolicy(null));
        assertFalse(ManiengineProtocol.capturesTouch("\"centre\""));
    }

    @Test
    public void isModelBox_includesTopLeftAndExcludesBottomRight() {
        assertTrue(ManiengineProtocol.isModelBox(16, 40, 16, 40, 344, 600));
        assertTrue(ManiengineProtocol.isModelBox(343, 599, 16, 40, 344, 600));
        assertFalse(ManiengineProtocol.isModelBox(15, 100, 16, 40, 344, 600));
        assertFalse(ManiengineProtocol.isModelBox(344, 100, 16, 40, 344, 600));
        assertFalse(ManiengineProtocol.isModelBox(100, 39, 16, 40, 344, 600));
        assertFalse(ManiengineProtocol.isModelBox(100, 600, 16, 40, 344, 600));
    }

    @Test
    public void touchBoxScript_publishesFractions() {
        assertEquals("window.tomodoomTouchBox={left:0.0500,top:0.0250,right:0.9500,bottom:0.8000};",
                ManiengineProtocol.touchBoxScript(0.05f, 0.025f, 0.95f, 0.8f));
    }

    @Test
    public void isTabFlick_needsAnEarlyFastHorizontalSwipe() {
        assertTrue(ManiengineProtocol.isTabFlick(1200f, 0f, 200L));
        assertTrue(ManiengineProtocol.isTabFlick(-1500f, 300f, 50L));
        assertFalse(ManiengineProtocol.isTabFlick(1199f, 0f, 50L));
        assertFalse(ManiengineProtocol.isTabFlick(3000f, 0f, 201L));
        assertFalse(ManiengineProtocol.isTabFlick(1500f, 1100f, 50L));
    }

    @Test
    public void bundledEntryPage_listensForTheStateEvent() throws Exception {
        String html = bundledEntryPage();
        assertTrue(html.contains(ManiengineProtocol.STATE_EVENT));
        assertTrue(html.contains(ManiengineProtocol.STATE_GLOBAL));
    }

    @Test
    public void bundledEntryPage_rotatesInsideTheHostsModelBox() throws Exception {
        String html = bundledEntryPage();
        assertTrue(html.contains("<meta name=\"" + ManiengineProtocol.TOUCH_META_NAME
                + "\" content=\"" + ManiengineProtocol.TOUCH_CENTRE + "\">"));
        assertTrue(html.contains("window." + ManiengineProtocol.TOUCH_BOX_GLOBAL));
    }

    private static String bundledEntryPage() throws Exception {
        File entry = new File("src/main/assets/" + ManiengineAssets.ROOT_DIR
                + ManiengineAssets.ENTRY_FILE);
        return new String(Files.readAllBytes(entry.toPath()), StandardCharsets.UTF_8);
    }
}
