package com.example.tomodoom.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class TokenUsageParserTest {

    @Test
    public void parse_readsOpenAiUsage() throws JSONException {
        JSONObject response = new JSONObject("{\"choices\":[],\"usage\":{\"prompt_tokens\":120,"
                + "\"completion_tokens\":30,\"total_tokens\":175}}");
        TokenUsageParser.Usage usage = TokenUsageParser.parse(response);
        assertNotNull(usage);
        assertEquals(120, usage.promptTokens);
        assertEquals(30, usage.completionTokens);
        assertEquals(175, usage.totalTokens); // e.g. thinking tokens on top
    }

    @Test
    public void parse_missingTotalIsSum() throws JSONException {
        TokenUsageParser.Usage usage = TokenUsageParser.parse(new JSONObject(
                "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"));
        assertNotNull(usage);
        assertEquals(15, usage.totalTokens);
    }

    @Test
    public void parse_noUsageOrZeroIsNull() throws JSONException {
        assertNull(TokenUsageParser.parse(new JSONObject("{\"choices\":[]}")));
        assertNull(TokenUsageParser.parse(new JSONObject("{\"usage\":{}}")));
    }
}
