package com.example.tomodoom.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Uses the real org.json (test dependency) instead of the android.jar stubs, so the bundled
 * Kafka export is parsed exactly as the app parses it.
 */
public class AgentProfileTest {

    /** Unit tests run with the module directory ({@code app/}) as working directory. */
    private static final File KAFKA_ASSET = new File("src/main/assets/agents/kafka_agent.json");
    private static final File CHARON_ASSET = new File("src/main/assets/agents/charon_agent.json");

    private static AgentProfile load(File asset) throws Exception {
        String json = new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
        return AgentProfileLoader.parse(new JSONObject(json));
    }

    @Test
    public void bundledKafkaConfig_parsesWithExportedSettings() throws Exception {
        String json = new String(Files.readAllBytes(KAFKA_ASSET.toPath()), StandardCharsets.UTF_8);
        AgentProfile p = AgentProfileLoader.parse(new JSONObject(json));
        assertEquals("Kafka", p.getName());
        assertEquals("deepseek-v4-pro", p.getModel());
        assertEquals(0.84, p.getTemperature(), 1e-9);
        assertEquals(5, p.getMemoryWindowTurns());
        assertTrue(p.getSystemPrompt().contains(AgentProfile.GREETING_TRIGGER));
    }

    @Test
    public void missingOptionalFields_useDefaults() throws Exception {
        AgentProfile p = AgentProfileLoader.parse(
                new JSONObject("{\"config\":{\"pre_prompt\":\"Be nice.\"}}"));
        assertEquals(AgentProfile.DEFAULT_MODEL, p.getModel());
        assertEquals(AgentProfile.DEFAULT_TEMPERATURE, p.getTemperature(), 1e-9);
        assertEquals(AgentProfile.DEFAULT_MEMORY_WINDOW_TURNS, p.getMemoryWindowTurns());
    }

    @Test(expected = IOException.class)
    public void emptyPrompt_isRejected() throws Exception {
        AgentProfileLoader.parse(new JSONObject("{\"config\":{\"pre_prompt\":\"  \"}}"));
    }

    @Test(expected = IOException.class)
    public void missingConfig_isRejected() throws Exception {
        AgentProfileLoader.parse(new JSONObject("{}"));
    }

    @Test
    public void constructor_clampsValues() {
        AgentProfile p = new AgentProfile(" ", "prompt", " ", 9.0, -3);
        assertEquals("Agent", p.getName());
        assertEquals(AgentProfile.DEFAULT_MODEL, p.getModel());
        assertEquals(2.0, p.getTemperature(), 1e-9);
        assertEquals(1, p.getMemoryWindowTurns());
    }

    @Test
    public void modelOverride_onlyWhenNonBlank() {
        AgentProfile p = new AgentProfile("Kafka", "prompt", "deepseek-v4-pro", 0.5, 5);
        assertSame(p, p.withModelOverride("  "));
        assertEquals("deepseek-chat", p.withModelOverride("deepseek-chat").getModel());
        assertEquals(0.5, p.withModelOverride("deepseek-chat").getTemperature(), 1e-9);
    }

    @Test
    public void bundledCharonConfig_parsesWithExportedSettings() throws Exception {
        AgentProfile p = load(CHARON_ASSET);
        assertEquals("Charon", p.getName());
        assertEquals(0.95, p.getTemperature(), 1e-9);
        assertEquals(5, p.getMemoryWindowTurns());
        assertTrue(p.getSystemPrompt().contains(AgentProfile.GREETING_TRIGGER));
    }

    @Test
    public void withPetName_replacesTheAgentsOwnNameInBothBundledPersonas() throws Exception {
        for (File asset : new File[]{KAFKA_ASSET, CHARON_ASSET}) {
            AgentProfile original = load(asset);
            AgentProfile pet = original.withPetName("Mochi");
            assertEquals("Mochi", pet.getName());
            assertFalse(pet.getSystemPrompt().contains(original.getName()));
            assertTrue(pet.getSystemPrompt().contains("You are Mochi"));
            assertEquals(original.getTemperature(), pet.getTemperature(), 1e-9);
            assertEquals(original.getModel(), pet.getModel());
        }
    }

    @Test
    public void withPetName_onlyReplacesWholeWords() {
        AgentProfile p = new AgentProfile("Kafka", "You are Kafka. Kafkaesque, Kafka's, Kafka2.",
                "m", 0.5, 5);
        assertEquals("You are Tomo. Kafkaesque, Tomo's, Kafka2.",
                p.withPetName("Tomo").getSystemPrompt());
    }

    @Test
    public void withPetName_treatsSpecialCharactersLiterally() {
        AgentProfile p = new AgentProfile("Ka.ka", "I am Ka.ka, not Kafka.", "m", 0.5, 5);
        assertEquals("I am $1 \\o/, not Kafka.",
                p.withPetName("$1 \\o/").getSystemPrompt());
    }

    @Test
    public void withPetName_blankOrSameName_returnsSameProfile() {
        AgentProfile p = new AgentProfile("Kafka", "You are Kafka.", "m", 0.5, 5);
        assertSame(p, p.withPetName("  "));
        assertSame(p, p.withPetName("Kafka"));
    }
}
