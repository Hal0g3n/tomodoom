package com.example.tomodoom.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class QuizMarkerParserTest {

    @Test
    public void noMarker_isUngradedAndUnchanged() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse("  Pick a topic, genius.  ");
        assertEquals(QuizMarkerParser.Verdict.NONE, r.getVerdict());
        assertEquals("Pick a topic, genius.", r.getText());
    }

    @Test
    public void trailingCorrectMarker_isStripped() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse(
                "Did you Google that? Next: what is 7 x 8?\n[[CORRECT]]");
        assertEquals(QuizMarkerParser.Verdict.CORRECT, r.getVerdict());
        assertEquals("Did you Google that? Next: what is 7 x 8?", r.getText());
    }

    @Test
    public void wrongMarker_isDetected() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse("[[WRONG]] Pathetic. It's 56.");
        assertEquals(QuizMarkerParser.Verdict.WRONG, r.getVerdict());
        assertEquals("Pathetic. It's 56.", r.getText());
    }

    @Test
    public void markerVariants_areTolerated() {
        assertEquals(QuizMarkerParser.Verdict.CORRECT,
                QuizMarkerParser.parse("Fine. [[ correct ]]").getVerdict());
        assertEquals(QuizMarkerParser.Verdict.WRONG,
                QuizMarkerParser.parse("No. [Wrong]").getVerdict());
        assertEquals(QuizMarkerParser.Verdict.CORRECT,
                QuizMarkerParser.parse("Ugh. [[[CORRECT]]]").getVerdict());
        assertEquals("Ugh.", QuizMarkerParser.parse("Ugh. [[[CORRECT]]]").getText());
    }

    @Test
    public void lastMarkerWins_andAllAreStripped() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse(
                "First [[WRONG]] then actually [[CORRECT]]");
        assertEquals(QuizMarkerParser.Verdict.CORRECT, r.getVerdict());
        assertFalse(r.getText().contains("[["));
    }

    @Test
    public void markerOnlyReply_fallsBackToNonEmptyText() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse("  [[CORRECT]]  ");
        assertEquals(QuizMarkerParser.Verdict.CORRECT, r.getVerdict());
        assertEquals(QuizMarkerParser.EMPTY_REPLY_FALLBACK, r.getText());
    }

    @Test
    public void plainWordsAreNotMarkers() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse("That is CORRECT, sadly.");
        assertEquals(QuizMarkerParser.Verdict.NONE, r.getVerdict());
        assertEquals("That is CORRECT, sadly.", r.getText());
    }

    @Test
    public void blankLinesLeftByMarker_areCollapsed() {
        QuizMarkerParser.Result r = QuizMarkerParser.parse("Right.\n\n[[CORRECT]]\n\n\nNext question?");
        assertEquals("Right.\n\nNext question?", r.getText());
    }
}
