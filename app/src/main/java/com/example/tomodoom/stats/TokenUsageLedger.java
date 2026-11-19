package com.example.tomodoom.stats;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tokens spent per LLM model, all time. Pure Java; not thread-safe ({@code StatsRepository} guards
 * it with its lock).
 */
public final class TokenUsageLedger {

    /** Totals for one model. Immutable. */
    public static final class ModelUsage {
        @NonNull
        private final String model;
        private final long requests;
        private final long promptTokens;
        private final long completionTokens;
        private final long totalTokens;

        public ModelUsage(@NonNull String model, long requests, long promptTokens,
                          long completionTokens, long totalTokens) {
            this.model = model;
            this.requests = requests;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        @NonNull
        public String getModel() {
            return model;
        }

        public long getRequests() {
            return requests;
        }

        public long getPromptTokens() {
            return promptTokens;
        }

        public long getCompletionTokens() {
            return completionTokens;
        }

        public long getTotalTokens() {
            return totalTokens;
        }

        @NonNull
        ModelUsage plus(long prompt, long completion, long total) {
            return new ModelUsage(model, requests + 1, promptTokens + prompt,
                    completionTokens + completion, totalTokens + total);
        }
    }

    private final Map<String, ModelUsage> byModel = new LinkedHashMap<>();

    /**
     * Adds one request. Negative counts are treated as 0; a missing total ({@code <= 0}) becomes
     * prompt + completion.
     */
    public void record(@NonNull String model, long promptTokens, long completionTokens,
                       long totalTokens) {
        long prompt = Math.max(0, promptTokens);
        long completion = Math.max(0, completionTokens);
        long total = totalTokens > 0 ? totalTokens : prompt + completion;
        ModelUsage current = byModel.get(model);
        if (current == null) {
            current = new ModelUsage(model, 0, 0, 0, 0);
        }
        byModel.put(model, current.plus(prompt, completion, total));
    }

    /** @return each model's totals, most tokens first. */
    @NonNull
    public List<ModelUsage> getUsage() {
        List<ModelUsage> list = new ArrayList<>(byModel.values());
        Collections.sort(list, (a, b) -> Long.compare(b.getTotalTokens(), a.getTotalTokens()));
        return Collections.unmodifiableList(list);
    }

    /** @return the totals over every model, under the model name {@code ""}. */
    @NonNull
    public ModelUsage getGrandTotal() {
        ModelUsage sum = new ModelUsage("", 0, 0, 0, 0);
        for (ModelUsage usage : byModel.values()) {
            sum = new ModelUsage("", sum.requests + usage.requests,
                    sum.promptTokens + usage.promptTokens,
                    sum.completionTokens + usage.completionTokens,
                    sum.totalTokens + usage.totalTokens);
        }
        return sum;
    }

    public boolean isEmpty() {
        return byModel.isEmpty();
    }

    @NonNull
    public TokenUsageLedger copy() {
        TokenUsageLedger copy = new TokenUsageLedger();
        copy.byModel.putAll(byModel);
        return copy;
    }

    @NonNull
    public JSONArray toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (ModelUsage usage : byModel.values()) {
            array.put(new JSONObject()
                    .put("model", usage.model)
                    .put("requests", usage.requests)
                    .put("prompt", usage.promptTokens)
                    .put("completion", usage.completionTokens)
                    .put("total", usage.totalTokens));
        }
        return array;
    }

    /** Reads {@link #toJson()} output, skipping malformed entries. */
    @NonNull
    public static TokenUsageLedger fromJson(@NonNull JSONArray array) {
        TokenUsageLedger ledger = new TokenUsageLedger();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            String model = o == null ? "" : o.optString("model", "");
            if (model.isEmpty()) {
                continue;
            }
            ledger.byModel.put(model, new ModelUsage(model, o.optLong("requests"),
                    o.optLong("prompt"), o.optLong("completion"), o.optLong("total")));
        }
        return ledger;
    }
}
