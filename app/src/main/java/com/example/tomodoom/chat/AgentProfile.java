package com.example.tomodoom.chat;

import androidx.annotation.NonNull;

/**
 * Immutable description of a chat agent (persona prompt and model settings), loaded from an
 * exported agent config such as {@code assets/agents/kafka_agent.json} by
 * {@link AgentProfileLoader}. Pure Java.
 */
public final class AgentProfile {

    public static final String DEFAULT_MODEL = "deepseek-v4-pro";
    public static final double DEFAULT_TEMPERATURE = 0.8;
    public static final int DEFAULT_MEMORY_WINDOW_TURNS = 5;
    /** Hidden first user turn that makes the agent greet the user (see the agent's prompt). */
    public static final String GREETING_TRIGGER = "__START__";

    @NonNull
    private final String name;
    @NonNull
    private final String systemPrompt;
    @NonNull
    private final String model;
    private final double temperature;
    private final int memoryWindowTurns;

    /**
     * Blank names/models fall back to defaults; temperature is clamped to [0, 2] and the memory
     * window to at least 1 turn.
     */
    public AgentProfile(@NonNull String name, @NonNull String systemPrompt, @NonNull String model,
                        double temperature, int memoryWindowTurns) {
        this.name = name.trim().isEmpty() ? "Agent" : name.trim();
        this.systemPrompt = systemPrompt;
        this.model = model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
        this.temperature = Double.isNaN(temperature)
                ? DEFAULT_TEMPERATURE : Math.max(0.0, Math.min(2.0, temperature));
        this.memoryWindowTurns = Math.max(1, memoryWindowTurns);
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @NonNull
    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMemoryWindowTurns() {
        return memoryWindowTurns;
    }

    /** A copy using {@code modelOverride} when it is non-blank, otherwise this profile. */
    @NonNull
    public AgentProfile withModelOverride(@NonNull String modelOverride) {
        if (modelOverride.trim().isEmpty()) {
            return this;
        }
        return new AgentProfile(name, systemPrompt, modelOverride, temperature, memoryWindowTurns);
    }

    @NonNull
    @Override
    public String toString() {
        return "AgentProfile{" + name + ", model=" + model + ", temperature=" + temperature
                + ", window=" + memoryWindowTurns + "}";
    }
}
