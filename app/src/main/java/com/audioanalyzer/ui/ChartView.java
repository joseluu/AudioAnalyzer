package com.audioanalyzer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.audioanalyzer.model.MeasurementPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChartView extends View {

    private final Paint paintAmplitude = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPhase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTooltip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTooltipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPoint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<MeasurementPoint> data = new ArrayList<>();
    private boolean logScale = true;

    // Chart margins
    private static final float MARGIN_LEFT = 80f;
    private static final float MARGIN_RIGHT = 80f;
    private static final float MARGIN_TOP = 40f;
    private static final float MARGIN_BOTTOM = 60f;

    // Y axis ranges
    private float dbMin = -30f;
    private float dbMax = 10f;
    private float phaseMin = -180f;
    private float phaseMax = 180f;

    // X axis range
    private double freqMin = 30.0;
    private double freqMax = 15000.0;

    // Zoom/pan
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float panX = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    // Tooltip
    private int tooltipIndex = -1;

    // Reference values (1 kHz)
    private double refAmplitude = 1.0;
    private double refPhase = 0.0;

    // Frequency grid labels
    private static final double[] FREQ_LABELS = {30, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 15000};
    private static final String[] FREQ_LABEL_TEXT = {"30", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "15k"};

    public ChartView(Context context) {
        super(context);
        init();
    }

    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintAmplitude.setColor(Color.rgb(0, 150, 255));
        paintAmplitude.setStrokeWidth(3f);
        paintAmplitude.setStyle(Paint.Style.STROKE);

        paintPhase.setColor(Color.rgb(255, 100, 0));
        paintPhase.setStrokeWidth(2f);
        paintPhase.setStyle(Paint.Style.STROKE);

        paintGrid.setColor(Color.rgb(80, 80, 80));
        paintGrid.setStrokeWidth(1f);

        paintAxis.setColor(Color.WHITE);
        paintAxis.setStrokeWidth(2f);

        paintLabel.setColor(Color.rgb(200, 200, 200));
        paintLabel.setTextSize(28f);

        paintTooltip.setColor(Color.WHITE);
        paintTooltip.setTextSize(26f);

        paintTooltipBg.setColor(Color.argb(200, 40, 40, 40));
        paintTooltipBg.setStyle(Paint.Style.FILL);

        paintPoint.setColor(Color.rgb(0, 150, 255));
        paintPoint.setStyle(Paint.Style.FILL);

        setBackgroundColor(Color.rgb(30, 30, 30));

        scaleDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector det) {
                        scaleX *= det.getScaleFactor();
                        scaleX = Math.max(1f, Math.min(scaleX, 10f));
                        invalidate();
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                        panX -= dx;
                        float maxPan = getChartWidth() * (scaleX - 1f);
                        panX = Math.max(-maxPan, Math.min(0, panX));
                        invalidate();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        handleTap(e.getX(), e.getY());
                        return true;
                    }
                });
    }

    public void setData(List<MeasurementPoint> data) {
        this.data = new ArrayList<>(data);
        updateReference();
        tooltipIndex = -1;
    }

    public void setLogScale(boolean logScale) {
        this.logScale = logScale;
    }

    private void updateReference() {
        double minDist = Double.MAX_VALUE;
        for (MeasurementPoint p : data) {
            double dist = Math.abs(p.frequency - 1000.0);
            if (dist < minDist) {
                minDist = dist;
                refAmplitude = p.amplitude;
                refPhase = p.phaseDegrees;
            }
        }
        if (refAmplitude <= 0) refAmplitude = 1.0;
    }

    private float getChartWidth() {
        return getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
    }

    private float getChartHeight() {
        return getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
    }

    private float freqToX(double freq) {
        float chartW = getChartWidth() * scaleX;
        float x;
        if (logScale) {
            double logMin = Math.log10(freqMin);
            double logMax = Math.log10(freqMax);
            x = (float) ((Math.log10(freq) - logMin) / (logMax - logMin) * chartW);
        } else {
            x = (float) ((freq - freqMin) / (freqMax - freqMin) * chartW);
        }
        return MARGIN_LEFT + x + panX;
    }

    private float dbToY(double db) {
        float chartH = getChartHeight();
        float ratio = (float) ((db - dbMin) / (dbMax - dbMin));
        return MARGIN_TOP + chartH * (1f - ratio);
    }

    private float phaseToY(double phase) {
        float chartH = getChartHeight();
        float ratio = (float) ((phase - phaseMin) / (phaseMax - phaseMin));
        return MARGIN_TOP + chartH * (1f - ratio);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        drawGrid(canvas);
        drawAxes(canvas);
        drawData(canvas);
        drawTooltip(canvas);
    }

    private void drawGrid(Canvas canvas) {
        // Frequency grid lines
        for (int i = 0; i < FREQ_LABELS.length; i++) {
            float x = freqToX(FREQ_LABELS[i]);
            if (x >= MARGIN_LEFT && x <= getWidth() - MARGIN_RIGHT) {
                canvas.drawLine(x, MARGIN_TOP, x, getHeight() - MARGIN_BOTTOM, paintGrid);
                canvas.drawText(FREQ_LABEL_TEXT[i], x - 15, getHeight() - MARGIN_BOTTOM + 35, paintLabel);
            }
        }

        // dB grid lines
        paintLabel.setTextAlign(Paint.Align.RIGHT);
        for (float db = dbMin; db <= dbMax; db += 10) {
            float y = dbToY(db);
            canvas.drawLine(MARGIN_LEFT, y, getWidth() - MARGIN_RIGHT, y, paintGrid);
            canvas.drawText(String.format(Locale.US, "%.0f", db), MARGIN_LEFT - 8, y + 8, paintLabel);
        }
        paintLabel.setTextAlign(Paint.Align.LEFT);
    }

    private void drawAxes(Canvas canvas) {
        // Left axis (dB)
        canvas.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, getHeight() - MARGIN_BOTTOM, paintAxis);
        // Right axis (phase)
        float rightX = getWidth() - MARGIN_RIGHT;
        canvas.drawLine(rightX, MARGIN_TOP, rightX, getHeight() - MARGIN_BOTTOM, paintAxis);
        // Bottom axis
        canvas.drawLine(MARGIN_LEFT, getHeight() - MARGIN_BOTTOM,
                rightX, getHeight() - MARGIN_BOTTOM, paintAxis);

        // Axis labels
        paintLabel.setTextAlign(Paint.Align.CENTER);
        Paint labelPaintDb = new Paint(paintLabel);
        labelPaintDb.setColor(paintAmplitude.getColor());
        canvas.drawText("dB", MARGIN_LEFT / 2, MARGIN_TOP - 10, labelPaintDb);

        Paint labelPaintPhase = new Paint(paintLabel);
        labelPaintPhase.setColor(paintPhase.getColor());
        canvas.drawText("Phase°", rightX + MARGIN_RIGHT / 2, MARGIN_TOP - 10, labelPaintPhase);

        // Phase labels on right axis
        paintLabel.setTextAlign(Paint.Align.LEFT);
        Paint phaseLabel = new Paint(paintLabel);
        phaseLabel.setColor(paintPhase.getColor());
        phaseLabel.setTextSize(24f);
        for (float p = phaseMin; p <= phaseMax; p += 90) {
            float y = phaseToY(p);
            canvas.drawText(String.format(Locale.US, "%.0f°", p), rightX + 8, y + 8, phaseLabel);
        }
        paintLabel.setTextAlign(Paint.Align.LEFT);
    }

    private void drawData(Canvas canvas) {
        if (data.size() < 2) return;

        Path ampPath = new Path();
        Path phasePath = new Path();
        boolean ampFirst = true;
        boolean phaseFirst = true;

        for (int i = 0; i < data.size(); i++) {
            MeasurementPoint p = data.get(i);
            float x = freqToX(p.frequency);
            float yDb = dbToY(p.getAmplitudeDb(refAmplitude));
            float yPhase = phaseToY(p.getRelativePhase(refPhase));

            if (x < MARGIN_LEFT || x > getWidth() - MARGIN_RIGHT) continue;

            if (ampFirst) {
                ampPath.moveTo(x, yDb);
                ampFirst = false;
            } else {
                ampPath.lineTo(x, yDb);
            }

            if (phaseFirst) {
                phasePath.moveTo(x, yPhase);
                phaseFirst = false;
            } else {
                phasePath.lineTo(x, yPhase);
            }

            // Draw small dots
            canvas.drawCircle(x, yDb, 3f, paintPoint);
        }

        canvas.drawPath(ampPath, paintAmplitude);
        canvas.drawPath(phasePath, paintPhase);
    }

    private void drawTooltip(Canvas canvas) {
        if (tooltipIndex < 0 || tooltipIndex >= data.size()) return;

        MeasurementPoint p = data.get(tooltipIndex);
        float x = freqToX(p.frequency);
        float yDb = dbToY(p.getAmplitudeDb(refAmplitude));

        String text = String.format(Locale.US, "%.1f Hz | %.1f dB | %.1f°",
                p.frequency, p.getAmplitudeDb(refAmplitude), p.getRelativePhase(refPhase));

        float textWidth = paintTooltip.measureText(text);
        float tooltipX = Math.max(MARGIN_LEFT, Math.min(x - textWidth / 2, getWidth() - MARGIN_RIGHT - textWidth - 20));
        float tooltipY = Math.max(MARGIN_TOP + 40, yDb - 40);

        RectF bg = new RectF(tooltipX - 10, tooltipY - 30, tooltipX + textWidth + 10, tooltipY + 8);
        canvas.drawRoundRect(bg, 8, 8, paintTooltipBg);
        canvas.drawText(text, tooltipX, tooltipY, paintTooltip);

        // Highlight point
        Paint highlight = new Paint(paintPoint);
        highlight.setColor(Color.YELLOW);
        canvas.drawCircle(x, yDb, 8f, highlight);
    }

    private void handleTap(float tapX, float tapY) {
        float minDist = Float.MAX_VALUE;
        int closest = -1;

        for (int i = 0; i < data.size(); i++) {
            MeasurementPoint p = data.get(i);
            float x = freqToX(p.frequency);
            float y = dbToY(p.getAmplitudeDb(refAmplitude));
            float dist = (float) Math.sqrt((x - tapX) * (x - tapX) + (y - tapY) * (y - tapY));
            if (dist < minDist && dist < 80) {
                minDist = dist;
                closest = i;
            }
        }

        tooltipIndex = closest;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }
}
