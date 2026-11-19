package com.example.tomodoom.ui.stats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.tomodoom.R;
import com.example.tomodoom.stats.HpHistory;

import java.util.Collections;
import java.util.List;

/**
 * The pet's HP over a time window, drawn as a step line with a soft fill and a guide at half
 * health. Data comes from {@code StatsReport} ({@link HpHistory.Sample}s, oldest first); the last
 * value is held until the window's end ("now").
 */
public class HpLineChartView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final Path fill = new Path();
    private final float labelGap;

    @NonNull
    private List<HpHistory.Sample> samples = Collections.emptyList();
    private long windowStart;
    private long windowEnd = 1;

    public HpLineChartView(@NonNull Context context) {
        this(context, null);
    }

    public HpLineChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(ContextCompat.getColor(context, R.color.chart_line));
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(linePaint.getColor());
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(ContextCompat.getColor(context, R.color.chart_fill));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.chart_grid));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.chart_label));
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
        labelPaint.setTypeface(StatsFormatter.chartTypeface(context));
        labelGap = 6 * density;
    }

    /** Shows {@code samples} over {@code [windowStart, windowEnd]}. */
    public void setData(@NonNull List<HpHistory.Sample> samples, long windowStart,
                        long windowEnd) {
        this.samples = samples;
        this.windowStart = windowStart;
        this.windowEnd = Math.max(windowStart + 1, windowEnd);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int maxHp = 1;
        for (HpHistory.Sample sample : samples) {
            maxHp = Math.max(maxHp, sample.getMaxHp());
        }
        String topLabel = String.valueOf(maxHp);
        float labelWidth = labelPaint.measureText(topLabel);
        float left = getPaddingLeft() + labelWidth + labelGap;
        float dotRadius = 2 * linePaint.getStrokeWidth();
        // Room for the dot and for the guide labels centred on the top and bottom lines.
        float inset = Math.max(dotRadius, (labelPaint.descent() - labelPaint.ascent()) / 2f);
        float right = getWidth() - getPaddingRight() - dotRadius;
        float top = getPaddingTop() + inset;
        float bottom = getHeight() - getPaddingBottom() - inset;
        if (right <= left || bottom <= top) {
            return;
        }

        // Guides at full, half and zero HP, labelled on the left.
        float textOffset = -(labelPaint.ascent() + labelPaint.descent()) / 2f;
        float[] fractions = {1f, 0.5f, 0f};
        for (float fraction : fractions) {
            float y = bottom - fraction * (bottom - top);
            canvas.drawLine(left, y, right, y, gridPaint);
            String label = String.valueOf(Math.round(fraction * maxHp));
            canvas.drawText(label, left - labelGap - labelPaint.measureText(label),
                    y + textOffset, labelPaint);
        }
        if (samples.isEmpty()) {
            return;
        }

        line.reset();
        fill.reset();
        float previousY = 0f;
        for (int i = 0; i < samples.size(); i++) {
            HpHistory.Sample sample = samples.get(i);
            float x = xFor(sample.getTimestampMillis(), left, right);
            float y = bottom - (float) sample.getHp() / maxHp * (bottom - top);
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, previousY);
                line.lineTo(x, y);
                fill.lineTo(x, previousY);
                fill.lineTo(x, y);
            }
            previousY = y;
        }
        line.lineTo(right, previousY);
        fill.lineTo(right, previousY);
        fill.lineTo(right, bottom);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
        // The current value, so a brand-new history (one sample at "now") still shows.
        canvas.drawCircle(right, previousY, dotRadius, dotPaint);
    }

    private float xFor(long timestamp, float left, float right) {
        float fraction = (float) (timestamp - windowStart) / (windowEnd - windowStart);
        fraction = Math.max(0f, Math.min(1f, fraction));
        return left + fraction * (right - left);
    }
}
