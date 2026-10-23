package com.example.smartirrigation.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.smartirrigation.R;

import java.util.Locale;

/**
 * 环形仪表盘：任意量程的指标（土壤含水、空气温度、空气湿度、光照强度…）。
 *
 * <p>数值变化时环形弧与中间读数一起做缓动动画。
 * value == null 时显示 “--”（未上报 / 传感器缺失），不绘制进度弧。</p>
 */
public class RingGaugeView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();

    /** null 表示暂无数据 */
    private Double rawValue;
    private double minValue = 0d;
    private double maxValue = 100d;
    private int decimals = 0;

    private String unit = "%";
    private String label = "土壤含水";
    private int progressColor;
    /** false 时只画环，数值与名称交给外部布局（用于紧凑指标格） */
    private boolean showText = true;

    private float animatedPercent;
    private float animatedValue;
    private ValueAnimator animator;

    public RingGaugeView(Context context) {
        this(context, null);
    }

    public RingGaugeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RingGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.ring_track));

        progressColor = ContextCompat.getColor(context, R.color.primary);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(progressColor);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextAlign(Paint.Align.LEFT);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        unitPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        unitPaint.setTextAlign(Paint.Align.LEFT);

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** @param value 0~100 百分比；null 表示暂无数据 */
    public void setValue(Integer value) {
        if (value == null) {
            clearValue();
            return;
        }
        setMetric(value.doubleValue(), 0d, 100d, 0);
    }

    /**
     * 设置任意量程的读数，并播放数值动画。
     *
     * @param value    读数；null 表示暂无数据
     * @param min      量程下限（用于换算环形弧度）
     * @param max      量程上限
     * @param decimals 小数位数
     */
    public void setMetric(Double value, double min, double max, int decimals) {
        if (value == null) {
            clearValue();
            return;
        }
        if (max <= min) {
            max = min + 1d;
        }
        boolean rangeChanged = min != minValue || max != maxValue;
        minValue = min;
        maxValue = max;
        this.decimals = Math.max(0, decimals);

        rawValue = value;
        if (rangeChanged) {
            animatedValue = 0f;
            animatedPercent = 0f;
        }
        animateTo(value.floatValue());
    }

    private void clearValue() {
        cancelAnimation();
        rawValue = null;
        animatedPercent = 0f;
        animatedValue = 0f;
        invalidate();
    }

    private void animateTo(float target) {
        cancelAnimation();
        animator = ValueAnimator.ofFloat(animatedValue, target);
        animator.setDuration(700L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animatedValue = (float) animation.getAnimatedValue();
                animatedPercent = percentOf(animatedValue);
                invalidate();
            }
        });
        animator.start();
    }

    /** 把读数换算成 0~100 的环形弧度；超出量程时弧度停在两端，读数照实显示 */
    private float percentOf(float value) {
        double percent = (value - minValue) / (maxValue - minValue) * 100d;
        return (float) Math.max(0d, Math.min(100d, percent));
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    public void setUnit(String unit) {
        this.unit = unit == null ? "" : unit;
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        invalidate();
    }

    public void setProgressColor(int color) {
        this.progressColor = color;
        progressPaint.setColor(color);
        invalidate();
    }

    public void setTextVisible(boolean visible) {
        this.showText = visible;
        invalidate();
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int modeW = MeasureSpec.getMode(widthMeasureSpec);
        int modeH = MeasureSpec.getMode(heightMeasureSpec);
        int size;
        if (modeW == MeasureSpec.UNSPECIFIED && modeH == MeasureSpec.UNSPECIFIED) {
            size = (int) dp(140);
        } else if (modeH == MeasureSpec.UNSPECIFIED) {
            size = width;
        } else if (modeW == MeasureSpec.UNSPECIFIED) {
            size = height;
        } else {
            size = Math.min(width, height);
        }
        if (size <= 0) {
            size = (int) dp(140);
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        float stroke = Math.max(dp(5), size * 0.072f);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - stroke / 2f - dp(2);

        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);

        ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(ringRect, -90f, 360f, false, trackPaint);

        boolean hasValue = rawValue != null;
        float percent = hasValue ? animatedPercent : 0f;
        if (percent > 0f) {
            canvas.drawArc(ringRect, -90f, 360f * percent / 100f, false, progressPaint);
        }

        if (!showText) {
            return;
        }

        // 字号随控件尺寸缩放，小尺寸下也能读清
        float valueSize = Math.max(sp(16), size * 0.19f);
        valuePaint.setTextSize(valueSize);
        unitPaint.setTextSize(valueSize * 0.44f);
        labelPaint.setTextSize(Math.max(sp(10), size * 0.078f));

        String valueText = hasValue ? formatValue(animatedValue) : "--";
        float valueWidth = valuePaint.measureText(valueText);
        float unitWidth = hasValue ? unitPaint.measureText(unit) : 0f;
        float gap = hasValue ? dp(2) : 0f;
        float startX = cx - (valueWidth + gap + unitWidth) / 2f;
        float centerY = cy - size * 0.055f;
        float baseline = centerY + valueSize * 0.35f;

        canvas.drawText(valueText, startX, baseline, valuePaint);
        if (hasValue) {
            canvas.drawText(unit, startX + valueWidth + gap, baseline - valueSize * 0.05f, unitPaint);
        }
        canvas.drawText(label, cx, cy + size * 0.245f, labelPaint);
    }

    private String formatValue(float value) {
        if (decimals <= 0) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.CHINA, "%." + decimals + "f", value);
    }
}
