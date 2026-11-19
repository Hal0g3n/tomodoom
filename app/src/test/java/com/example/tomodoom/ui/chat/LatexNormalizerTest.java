package com.example.tomodoom.ui.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LatexNormalizerTest {

    private static String n(String s) {
        return LatexNormalizer.normalize(s);
    }

    @Test
    public void plainTextIsUnchanged() {
        assertEquals("Hello **there**", n("Hello **there**"));
    }

    @Test
    public void singleDollarBecomesInline() {
        assertEquals("Euler: $$e^{i\\pi}+1=0$$.", n("Euler: $e^{i\\pi}+1=0$."));
    }

    @Test
    public void parenDelimitersBecomeInline() {
        assertEquals("so $$x^2$$ grows", n("so \\(x^2\\) grows"));
    }

    @Test
    public void inlineDoubleDollarStaysInline() {
        assertEquals("area $$\\pi r^2$$ here", n("area $$\\pi r^2$$ here"));
    }

    @Test
    public void bracketDelimitersBecomeBlock() {
        assertEquals("Formula:\n\n$$\nx = \\frac{-b}{2a}\n$$\n\n Done.",
                n("Formula: \\[ x = \\frac{-b}{2a} \\] Done."));
    }

    @Test
    public void standaloneDoubleDollarBecomesBlock() {
        assertEquals("Look:\n\n$$\n\\int_0^1 x\\,dx\n$$\n\n\nNext",
                n("Look:\n$$\\int_0^1 x\\,dx$$\nNext"));
    }

    @Test
    public void multiLineDisplayBlockLosesBlankLines() {
        assertEquals("$$\na\n= b\n$$\n\n", n("$$\na\n\n= b\n$$"));
    }

    @Test
    public void currencyIsNotMaths() {
        assertEquals("it costs $5 and $10 now", n("it costs $5 and $10 now"));
        assertEquals("$5, then $$x$$", n("$5, then $x$"));
    }

    @Test
    public void spacedDollarsAreNotMaths() {
        assertEquals("a $ b $ c", n("a $ b $ c"));
    }

    @Test
    public void closingDollarFollowedByDigitIsNotMaths() {
        assertEquals("$a$1", n("$a$1"));
    }

    @Test
    public void singleDollarDoesNotSpanParagraphs() {
        assertEquals("$a\n\nb$", n("$a\n\nb$"));
    }

    @Test
    public void escapedDollarIsKept() {
        assertEquals("\\$5 and \\$x\\$", n("\\$5 and \\$x\\$"));
    }

    @Test
    public void inlineCodeIsUntouched() {
        assertEquals("run `echo $HOME $x$` then $$y$$", n("run `echo $HOME $x$` then $y$"));
        assertEquals("``a ` $b$``", n("``a ` $b$``"));
    }

    @Test
    public void unclosedDelimitersAreLeftAlone() {
        assertEquals("open \\( x", n("open \\( x"));
        assertEquals("open $$ x", n("open $$ x"));
        assertEquals("open $x", n("open $x"));
    }

    @Test
    public void emptyMathsIsDropped() {
        assertEquals("a  b", n("a \\(\\) b"));
    }
}
