package com.example.tomodoom.ui.stats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.tomodoom.R;

/**
 * A row of rounded bars with a value label above and a name below each. The last bar (today) is
 * highlighted. Used for minutes of doom-scrolling per day.
 */
public class BarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float density;

    @NonNull
    private long[] values = new long[0];
    @NonNull
    private String[] valueLabels = new String[0];
    @NonNull
    private String[] names = new String[0];

    public BarChartView(@NonNull Context context) {
        this(context, null);
    }

    public BarChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        barPaint.setColor(ContextCompat.getColor(context, R.color.chart_bar));
        highlightPaint.setColor(ContextCompat.getColor(context, R.color.chart_bar_today));
        basePaint.setColor(ContextCompat.getColor(context, R.color.chart_grid));
        basePaint.setStrokeWidth(density);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.chart_label));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
        labelPaint.setTypeface(StatsFormatter.chartTypeface(context));
    }

    /**
     * @param values      bar heights (≥ 0), oldest first.
     * @param valueLabels text above each bar (empty for none).
     * @param names       text below each bar.
     */
    public void setData(@NonNull long[] values, @NonNull String[] valueLabels,
                        @NonNull String[] names) {
        if (values.length != valueLabels.length || values.length != names.length) {
            throw new IllegalArgumentException("arrays must have the same length");
        }
        this.values = values.clone();
        this.valueLabels = valueLabels.clone();
        this.names = names.clone();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int count = values.length;
        if (count == 0) {
            return;
        }
        float textHeight = labelPaint.descent() - labelPaint.ascent();
        float gap = 4 * density;
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop() + textHeight + gap;
        float baseline = getHeight() - getPaddingBottom() - textHeight - gap;
        if (right <= left || baseline <= top) {
            return;
        }
        long max = 1;
        for (long value : values) {
            max = Math.max(max, value);
        }
        float slot = (right - left) / count;
        float barWidth = Math.min(slot * 0.6f, 28 * density);
        float radius = Math.min(barWidth / 2f, 6 * density);
        for (int i = 0; i < count; i++) {
            float centre = left + slot * (i + 0.5f);
            float height = (float) values[i] / max * (baseline - top);
            if (values[i] > 0) {
                // At least a sliver, so a short day is still visible.
                height = Math.max(height, 2 * density);
                bar.set(centre - barWidth / 2f, baseline - height, centre + barWidth / 2f,
                        baseline);
                canvas.drawRoundRect(bar, radius, radius,
                        i == count - 1 ? highlightPaint : barPaint);
            }
            if (!valueLabels[i].isEmpty()) {
                canvas.drawText(valueLabels[i], centre, baseline - height - gap
                        - labelPaint.descent(), labelPaint);
            }
            canvas.drawText(names[i], centre, baseline + gap - labelPaint.ascent(), labelPaint);
        }
        canvas.drawLine(left, baseline, right, baseline, basePaint);
    }
}
