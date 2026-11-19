package com.example.tomodoom.ui.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MarkdownSegmentsTest {

    @Test
    public void proseOnlyIsOneSegment() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("Hello\n\nworld");
        assertEquals(1, s.size());
        assertFalse(s.get(0).isCode());
        assertEquals("Hello\n\nworld", s.get(0).getText());
    }

    @Test
    public void fencedBlockIsSplitOutWithLanguage() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split(
                "Try this:\n```python\nprint(\"$x$\")\n  x = 1\n```\nThen run it.");
        assertEquals(3, s.size());
        assertEquals("Try this:", s.get(0).getText());
        assertTrue(s.get(1).isCode());
        assertEquals("python", s.get(1).getLanguage());
        assertEquals("print(\"$x$\")\n  x = 1", s.get(1).getText());
        assertEquals("Then run it.", s.get(2).getText());
    }

    @Test
    public void fenceWithoutLanguageAndTildes() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("~~~\na\n~~~");
        assertEquals(1, s.size());
        assertTrue(s.get(0).isCode());
        assertNull(s.get(0).getLanguage());
        assertEquals("a", s.get(0).getText());
    }

    @Test
    public void infoStringKeepsOnlyFirstWord() {
        assertEquals("js", MarkdownSegments.split("```js title=x\n1\n```").get(0).getLanguage());
    }

    @Test
    public void shorterOrOtherFenceDoesNotClose() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("````\n```\n~~~\n````\nafter");
        assertEquals(2, s.size());
        assertEquals("```\n~~~", s.get(0).getText());
        assertEquals("after", s.get(1).getText());
    }

    @Test
    public void unclosedFenceRunsToTheEnd() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("Code:\n```java\nint a;\n");
        assertEquals(2, s.size());
        assertTrue(s.get(1).isCode());
        assertEquals("int a;", s.get(1).getText());
    }

    @Test
    public void indentedFenceIsStrippedOfItsIndent() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("  ```\n  a\n    b\n  ```");
        assertEquals("a\n  b", s.get(0).getText());
    }

    @Test
    public void inlineTripleBackticksAreNotAFence() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("```x``` inline");
        assertEquals(1, s.size());
        assertFalse(s.get(0).isCode());
    }

    @Test
    public void whitespaceOnlyProseIsDropped() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("\n```\na\n```\n\n");
        assertEquals(1, s.size());
        assertTrue(s.get(0).isCode());
    }

    @Test
    public void crlfIsNormalised() {
        List<MarkdownSegments.Segment> s = MarkdownSegments.split("a\r\n```\r\nb\r\n```");
        assertEquals("a", s.get(0).getText());
        assertEquals("b", s.get(1).getText());
    }
}
