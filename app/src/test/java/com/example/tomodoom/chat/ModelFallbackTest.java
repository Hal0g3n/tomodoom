package com.example.tomodoom.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModelFallbackTest {

    @Test
    public void parseModels_splitsTrimsAndDropsBlanksAndDuplicates() {
        assertEquals(Arrays.asList("gemini-3.5-flash", "gemini-3.1-flash-lite", "lite"),
                ModelFallback.parseModels(
                        " gemini-3.5-flash, gemini-3.1-flash-lite ,,lite, gemini-3.5-flash "));
    }

    @Test
    public void parseModels_singleAndEmpty() {
        assertEquals(Collections.singletonList("deepseek-chat"),
                ModelFallback.parseModels("deepseek-chat"));
        assertTrue(ModelFallback.parseModels("").isEmpty());
        assertTrue(ModelFallback.parseModels(" , ").isEmpty());
    }

    @Test
    public void attemptPlan_primaryTwiceThenFallbacks() {
        assertEquals(Arrays.asList("a", "a", "b", "c"),
                ModelFallback.attemptPlan("a", Arrays.asList("b", "c")));
    }

    @Test
    public void attemptPlan_noFallbacksRetriesPrimaryOnce() {
        assertEquals(Arrays.asList("a", "a"),
                ModelFallback.attemptPlan("a", Collections.<String>emptyList()));
    }

    @Test
    public void attemptPlan_isCappedAndSkipsDuplicates() {
        List<String> plan = ModelFallback.attemptPlan("a", Arrays.asList("a", "b", "b", "c", "d"));
        assertEquals(Arrays.asList("a", "a", "b", "c"), plan);
        assertEquals(ModelFallback.MAX_ATTEMPTS, plan.size());
    }

    @Test
    public void backoff_growsAfterTheFirstAttempt() {
        assertEquals(0, ModelFallback.backoffMillisBefore(0));
        assertEquals(1_000, ModelFallback.backoffMillisBefore(1));
        assertEquals(2_000, ModelFallback.backoffMillisBefore(2));
        assertEquals(2_000, ModelFallback.backoffMillisBefore(3));
    }

    @Test
    public void temporaryFailuresAreRetryable() {
        for (int status : new int[]{408, 429, 500, 502, 503, 504}) {
            assertTrue("HTTP " + status, ModelFallback.isRetryable(http(status)));
        }
        assertTrue(ModelFallback.isRetryable(new SocketTimeoutException("read timed out")));
    }

    @Test
    public void clientErrorsAndOtherFailuresAreFinal() {
        for (int status : new int[]{400, 401, 403, 404, 422}) {
            assertFalse("HTTP " + status, ModelFallback.isRetryable(http(status)));
        }
        assertFalse(ModelFallback.isRetryable(new IOException("API URL must use HTTPS")));
    }

    @Test
    public void nextAttempt_busyRetriesTheSameModelFirst() {
        List<String> plan = Arrays.asList("a", "a", "b", "c");
        assertEquals(1, ModelFallback.nextAttemptIndex(plan, 0, http(503)));
        assertEquals(2, ModelFallback.nextAttemptIndex(plan, 1, http(429)));
        assertEquals(3, ModelFallback.nextAttemptIndex(plan, 2, http(503)));
        assertEquals(-1, ModelFallback.nextAttemptIndex(plan, 3, http(503)));
    }

    @Test
    public void nextAttempt_timeoutSkipsToTheNextModel() {
        List<String> plan = Arrays.asList("a", "a", "b", "c");
        assertEquals(2, ModelFallback.nextAttemptIndex(plan, 0, new SocketTimeoutException()));
        assertEquals(-1, ModelFallback.nextAttemptIndex(Arrays.asList("a", "a"), 0,
                new SocketTimeoutException()));
    }

    @Test
    public void nextAttempt_finalErrorsGiveUpAtOnce() {
        List<String> plan = Arrays.asList("a", "a", "b", "c");
        assertEquals(-1, ModelFallback.nextAttemptIndex(plan, 0, http(401)));
        assertEquals(-1, ModelFallback.nextAttemptIndex(plan, 0, http(404)));
    }

    @Test
    public void httpStatusException_keepsTheUserFacingMessage() {
        HttpStatusException error = http(503);
        assertEquals(503, error.getStatusCode());
        assertEquals("Gemini request failed with HTTP 503", error.getMessage());
    }

    private static HttpStatusException http(int status) {
        return new HttpStatusException(status, "Gemini request failed with HTTP " + status);
    }
}
