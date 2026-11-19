package com.example.tomodoom.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public class TokenUsageLedgerTest {

    @Test
    public void record_sumsPerModelAndSortsByTotal() {
        TokenUsageLedger ledger = new TokenUsageLedger();
        ledger.record("small", 10, 5, 15);
        ledger.record("big", 100, 50, 150);
        ledger.record("small", 20, 10, 30);

        List<TokenUsageLedger.ModelUsage> usage = ledger.getUsage();
        assertEquals(2, usage.size());
        assertEquals("big", usage.get(0).getModel());
        TokenUsageLedger.ModelUsage small = usage.get(1);
        assertEquals(2, small.getRequests());
        assertEquals(30, small.getPromptTokens());
        assertEquals(15, small.getCompletionTokens());
        assertEquals(45, small.getTotalTokens());

        TokenUsageLedger.ModelUsage total = ledger.getGrandTotal();
        assertEquals(3, total.getRequests());
        assertEquals(195, total.getTotalTokens());
    }

    @Test
    public void record_missingTotalIsPromptPlusCompletion_negativeIsZero() {
        TokenUsageLedger ledger = new TokenUsageLedger();
        ledger.record("m", 7, -3, 0);
        TokenUsageLedger.ModelUsage usage = ledger.getUsage().get(0);
        assertEquals(0, usage.getCompletionTokens());
        assertEquals(7, usage.getTotalTokens());
    }

    @Test
    public void json_roundTrips() throws JSONException {
        TokenUsageLedger ledger = new TokenUsageLedger();
        ledger.record("a", 1, 2, 3);
        ledger.record("b", 4, 5, 9);
        TokenUsageLedger copy = TokenUsageLedger.fromJson(ledger.toJson());
        assertEquals(12, copy.getGrandTotal().getTotalTokens());
        assertEquals(2, copy.getGrandTotal().getRequests());
    }

    @Test
    public void copy_isIndependent() {
        TokenUsageLedger ledger = new TokenUsageLedger();
        TokenUsageLedger copy = ledger.copy();
        ledger.record("a", 1, 1, 2);
        assertTrue(copy.isEmpty());
    }
}
