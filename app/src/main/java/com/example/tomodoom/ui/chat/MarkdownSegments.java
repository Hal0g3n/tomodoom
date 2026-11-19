package com.example.tomodoom.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits a chat reply into prose and fenced code blocks, in order. Pure Java.
 *
 * <p>Prose goes through Markdown + LaTeX rendering; each code block gets its own monospace,
 * horizontally scrollable view. A fence is a line of three or more backticks or tildes, indented
 * by at most three spaces, optionally followed by a language tag. It closes on a line holding
 * only the same character, at least as many times. An unclosed fence runs to the end of the
 * text (what a truncated reply looks like).
 */
public final class MarkdownSegments {

    /** One run of prose or one code block. */
    public static final class Segment {
        private final boolean code;
        @Nullable
        private final String language;
        @NonNull
        private final String text;

        private Segment(boolean code, @Nullable String language, @NonNull String text) {
            this.code = code;
            this.language = language;
            this.text = text;
        }

        @NonNull
        static Segment prose(@NonNull String text) {
            return new Segment(false, null, text);
        }

        @NonNull
        static Segment code(@Nullable String language, @NonNull String text) {
            return new Segment(true, language, text);
        }

        public boolean isCode() {
            return code;
        }

        /** The fence's language tag ({@code python}), or {@code null} if it had none. */
        @Nullable
        public String getLanguage() {
            return language;
        }

        /** Prose Markdown, or the code without its fences and without a trailing newline. */
        @NonNull
        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return (code ? "CODE(" + language + ")" : "PROSE") + "[" + text + "]";
        }
    }

    private MarkdownSegments() {
    }

    /** Splits {@code markdown} into segments. Whitespace-only prose is dropped. */
    @NonNull
    public static List<Segment> split(@NonNull String markdown) {
        List<Segment> segments = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        StringBuilder prose = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            Fence open = openingFence(lines[i]);
            if (open == null) {
                if (prose.length() > 0) {
                    prose.append('\n');
                }
                prose.append(lines[i]);
                i++;
                continue;
            }
            addProse(segments, prose);
            prose.setLength(0);
            StringBuilder code = new StringBuilder();
            i++;
            boolean first = true;
            while (i < lines.length && !closes(lines[i], open)) {
                if (!first) {
                    code.append('\n');
                }
                code.append(stripIndent(lines[i], open.indent));
                first = false;
                i++;
            }
            i++; // skip the closing fence (past the end if unclosed)
            String text = code.toString();
            while (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            segments.add(Segment.code(open.language, text));
        }
        addProse(segments, prose);
        return Collections.unmodifiableList(segments);
    }

    private static void addProse(@NonNull List<Segment> segments, @NonNull StringBuilder prose) {
        String text = prose.toString();
        if (!text.trim().isEmpty()) {
            segments.add(Segment.prose(text));
        }
    }

    private static final class Fence {
        final char mark;
        final int length;
        final int indent;
        @Nullable
        final String language;

        Fence(char mark, int length, int indent, @Nullable String language) {
            this.mark = mark;
            this.length = length;
            this.indent = indent;
            this.language = language;
        }
    }

    @Nullable
    private static Fence openingFence(@NonNull String line) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent >= line.length()) {
            return null;
        }
        char mark = line.charAt(indent);
        if (mark != '`' && mark != '~') {
            return null;
        }
        int end = indent;
        while (end < line.length() && line.charAt(end) == mark) {
            end++;
        }
        int length = end - indent;
        if (length < 3) {
            return null;
        }
        String info = line.substring(end).trim();
        if (mark == '`' && info.indexOf('`') >= 0) {
            return null; // inline code such as ```x``` on one line, not a fence
        }
        String language = null;
        if (!info.isEmpty()) {
            int space = indexOfWhitespace(info);
            language = space < 0 ? info : info.substring(0, space);
        }
        return new Fence(mark, length, indent, language);
    }

    private static boolean closes(@NonNull String line, @NonNull Fence open) {
        int indent = leadingSpaces(line);
        if (indent > 3) {
            return false;
        }
        String rest = line.substring(indent).trim();
        if (rest.length() < open.length) {
            return false;
        }
        for (int k = 0; k < rest.length(); k++) {
            if (rest.charAt(k) != open.mark) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static String stripIndent(@NonNull String line, int indent) {
        int strip = Math.min(indent, leadingSpaces(line));
        return line.substring(strip);
    }

    private static int leadingSpaces(@NonNull String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static int indexOfWhitespace(@NonNull String s) {
        for (int k = 0; k < s.length(); k++) {
            if (Character.isWhitespace(s.charAt(k))) {
                return k;
            }
        }
        return -1;
    }
}
