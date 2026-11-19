package com.example.tomodoom.ui.chat;

import androidx.annotation.NonNull;

/**
 * Rewrites the maths delimiters LLMs emit into the one syntax Markwon's LaTeX plugin reads.
 * Pure Java.
 *
 * <p>Markwon only understands {@code $$…$$}: inside a paragraph it's inline maths, and on its own
 * lines ({@code $$} / formula / {@code $$}) it's a display block. This maps:
 * <ul>
 *   <li>{@code \[…\]}, and {@code $$…$$} standing alone on its line(s) → a display block;</li>
 *   <li>{@code \(…\)}, {@code $…$} and {@code $$…$$} inside a sentence → inline {@code $$…$$}.</li>
 * </ul>
 * A single {@code $} only opens maths when the next character isn't a space and only closes it
 * when the previous character isn't a space and the next isn't a digit (Pandoc's rule), and it
 * never spans a blank line, so "it costs $5 and $10" stays text. {@code \$} stays an escaped
 * dollar, and inline code ({@code `…`}) is copied untouched. Feed it prose only: fenced code is
 * split off first by {@link MarkdownSegments}.
 */
public final class LatexNormalizer {

    private LatexNormalizer() {
    }

    @NonNull
    public static String normalize(@NonNull String prose) {
        if (prose.indexOf('$') < 0 && prose.indexOf('\\') < 0) {
            return prose;
        }
        StringBuilder out = new StringBuilder(prose.length() + 16);
        int n = prose.length();
        int i = 0;
        while (i < n) {
            char c = prose.charAt(i);
            if (c == '`') {
                int run = runLength(prose, i, '`');
                int close = findBacktickRun(prose, i + run, run);
                int end = close < 0 ? i + run : close + run;
                out.append(prose, i, end);
                i = end;
            } else if (c == '\\' && i + 1 < n) {
                char next = prose.charAt(i + 1);
                if (next == '(' || next == '[') {
                    String closer = next == '(' ? "\\)" : "\\]";
                    int close = prose.indexOf(closer, i + 2);
                    if (close >= 0) {
                        String tex = prose.substring(i + 2, close);
                        if (next == '(') {
                            appendInline(out, tex);
                        } else {
                            appendBlock(out, tex);
                        }
                        i = close + 2;
                        continue;
                    }
                }
                // Any other escape (\$, \\, \*) is kept for Markdown to handle.
                out.append(c).append(next);
                i += 2;
            } else if (c == '$' && i + 1 < n && prose.charAt(i + 1) == '$') {
                int close = findDoubleDollar(prose, i + 2);
                if (close < 0) {
                    out.append("$$");
                    i += 2;
                    continue;
                }
                String tex = prose.substring(i + 2, close);
                if (aloneOnLine(prose, i, close + 2)) {
                    appendBlock(out, tex);
                } else {
                    appendInline(out, tex);
                }
                i = close + 2;
            } else if (c == '$') {
                int close = findSingleDollar(prose, i);
                if (close < 0) {
                    out.append(c);
                    i++;
                    continue;
                }
                appendInline(out, prose.substring(i + 1, close));
                i = close + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void appendInline(@NonNull StringBuilder out, @NonNull String tex) {
        String body = tex.replace('\n', ' ').trim();
        if (body.isEmpty()) {
            return;
        }
        out.append("$$").append(body).append("$$");
    }

    private static void appendBlock(@NonNull StringBuilder out, @NonNull String tex) {
        String body = tex.trim();
        if (body.isEmpty()) {
            return;
        }
        // Blank lines would end the block early; a single newline is fine for LaTeX.
        while (body.contains("\n\n")) {
            body = body.replace("\n\n", "\n");
        }
        trimTrailingSpaces(out);
        if (out.length() > 0) {
            out.append(out.charAt(out.length() - 1) == '\n' ? "\n" : "\n\n");
        }
        out.append("$$\n").append(body).append("\n$$\n\n");
    }

    private static void trimTrailingSpaces(@NonNull StringBuilder out) {
        int len = out.length();
        while (len > 0 && (out.charAt(len - 1) == ' ' || out.charAt(len - 1) == '\t')) {
            len--;
        }
        out.setLength(len);
    }

    /** Whether only whitespace sits between the line start and {@code start}, and {@code end} and the line end. */
    private static boolean aloneOnLine(@NonNull String s, int start, int end) {
        for (int k = start - 1; k >= 0 && s.charAt(k) != '\n'; k--) {
            if (!Character.isWhitespace(s.charAt(k))) {
                return false;
            }
        }
        for (int k = end; k < s.length() && s.charAt(k) != '\n'; k++) {
            if (!Character.isWhitespace(s.charAt(k))) {
                return false;
            }
        }
        return true;
    }

    private static int findDoubleDollar(@NonNull String s, int from) {
        for (int k = from; k + 1 < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') {
                k++; // skip the escaped character
            } else if (c == '$' && s.charAt(k + 1) == '$') {
                return k;
            }
        }
        return -1;
    }

    /** The closing {@code $} for the single {@code $} at {@code open}, or -1 if it isn't maths. */
    private static int findSingleDollar(@NonNull String s, int open) {
        int n = s.length();
        if (open + 1 >= n || Character.isWhitespace(s.charAt(open + 1))) {
            return -1;
        }
        for (int k = open + 1; k < n; k++) {
            char c = s.charAt(k);
            if (c == '\\') {
                k++;
            } else if (c == '\n' && isBlankLineAfter(s, k)) {
                return -1; // maths never spans paragraphs
            } else if (c == '$') {
                if (k + 1 < n && s.charAt(k + 1) == '$') {
                    return -1;
                }
                boolean spaceBefore = Character.isWhitespace(s.charAt(k - 1));
                boolean digitAfter = k + 1 < n && Character.isDigit(s.charAt(k + 1));
                if (!spaceBefore && !digitAfter && k > open + 1) {
                    return k;
                }
                return -1; // "$5 and $10": the second $ opens nothing either
            }
        }
        return -1;
    }

    private static boolean isBlankLineAfter(@NonNull String s, int newline) {
        for (int k = newline + 1; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\n') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private static int runLength(@NonNull String s, int from, char c) {
        int k = from;
        while (k < s.length() && s.charAt(k) == c) {
            k++;
        }
        return k - from;
    }

    /** Start of the next run of exactly {@code length} backticks at or after {@code from}, or -1. */
    private static int findBacktickRun(@NonNull String s, int from, int length) {
        int k = from;
        while (k < s.length()) {
            if (s.charAt(k) == '`') {
                int run = runLength(s, k, '`');
                if (run == length) {
                    return k;
                }
                k += run;
            } else {
                k++;
            }
        }
        return -1;
    }
}
