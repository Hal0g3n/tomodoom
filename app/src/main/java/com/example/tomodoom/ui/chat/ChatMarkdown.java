package com.example.tomodoom.ui.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spanned;
import android.util.LruCache;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.tomodoom.R;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;

/**
 * Renders the prose of a bot reply: Markdown (bold, lists, headings, inline code) plus LaTeX
 * maths, typeset offline by JLatexMath. Fenced code blocks never get here; {@link ChatAdapter}
 * splits them off with {@link MarkdownSegments} and shows them in their own view.
 *
 * <p>Delimiters are normalised first ({@link LatexNormalizer}), since Markwon only reads
 * {@code $$…$$}. Formulas are rendered on Markwon's own background executor and appear in place
 * when ready. Parsed text is cached per prose string, so scrolling back doesn't re-parse.
 * One instance per chat page; main thread only.
 */
@MainThread
final class ChatMarkdown {

    private static final int CACHE_SIZE = 64;

    @NonNull
    private final Markwon markwon;
    @NonNull
    private final LruCache<String, Spanned> cache = new LruCache<>(CACHE_SIZE);

    /**
     * @param scale the chat text size as a multiple of the default (Settings); formulas are
     *              typeset at that size, fixed for this instance.
     */
    ChatMarkdown(@NonNull Context context, float scale) {
        float textSize = context.getResources().getDimension(R.dimen.chat_bubble_text_size) * scale;
        int mathColor = ContextCompat.getColor(context, R.color.chat_math_text);
        int inlineCodeBackground = ContextCompat.getColor(context, R.color.chat_inline_code_background);
        int codeBlockBackground = ContextCompat.getColor(context, R.color.chat_code_block_background);
        int codeBlockText = ContextCompat.getColor(context, R.color.chat_code_block_text);
        // Fira Code Retina, as in the fenced code blocks (bundled, so loading it is synchronous).
        Typeface loaded = ResourcesCompat.getFont(context, R.font.fira_code_retina);
        Typeface codeTypeface = loaded != null ? loaded : Typeface.MONOSPACE;
        markwon = Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(JLatexMathPlugin.create(textSize, builder -> {
                    builder.inlinesEnabled(true);
                    builder.theme().textColor(mathColor);
                }))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder theme) {
                        theme.codeTypeface(codeTypeface)
                                .codeBackgroundColor(inlineCodeBackground)
                                // Indented (non-fenced) code blocks stay in the text; match
                                // the fenced blocks' colours.
                                .codeBlockBackgroundColor(codeBlockBackground)
                                .codeBlockTextColor(codeBlockText);
                    }
                })
                .build();
    }

    /** Shows {@code prose} (Markdown with maths) in {@code view}. */
    void show(@NonNull TextView view, @NonNull String prose) {
        Spanned spanned = cache.get(prose);
        if (spanned == null) {
            spanned = markwon.toMarkdown(LatexNormalizer.normalize(prose));
            cache.put(prose, spanned);
        }
        markwon.setParsedMarkdown(view, spanned);
    }
}
