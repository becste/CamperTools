package com.campertools.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class LevelView extends View {

    // Non-linear gain to exaggerate small tilts while keeping output in [-1, 1]
    private static final float HYPERBOLIC_GAIN = 2.0f;
    private static final float HYPERBOLIC_NORMALIZER = (float) Math.tanh(HYPERBOLIC_GAIN);

    // Normalized tilt values in range -1..1
    private float tiltX = 0f;  // left/right
    private float tiltY = 0f;  // up/down

    private Paint circlePaint;
    private Paint bubblePaint;
    private Paint barPaint;
    private Paint barBubblePaint;
    private Paint linePaint;
    private Paint centerLinePaint;
    private final RectF vBarRect = new RectF();
    private final RectF hBarRect = new RectF();

    public LevelView(Context context) {
        super(context);
        init(context);
    }

    public LevelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LevelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        int secondaryColor = ContextCompat.getColor(context, R.color.secondary_text);
        int primaryColor = ContextCompat.getColor(context, R.color.highlight_color);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(6f);
        circlePaint.setColor(secondaryColor);

        bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(primaryColor);
        bubblePaint.setAlpha(200); // Make bubble translucent

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(6f);
        barPaint.setColor(secondaryColor);

        barBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBubblePaint.setStyle(Paint.Style.FILL);
        barBubblePaint.setColor(primaryColor);
        barBubblePaint.setAlpha(200); // Make bubble translucent

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setColor(secondaryColor);
        linePaint.setAlpha(150);

        centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setStrokeWidth(5f); // Thicker line
        centerLinePaint.setColor(secondaryColor);
        centerLinePaint.setAlpha(200);
    }

    public void setNightMode(boolean isNightMode) {
        int highlightColor;
        int secondaryColor;

        if (isNightMode) {
            highlightColor = ContextCompat.getColor(getContext(), R.color.red_500);
            secondaryColor = ContextCompat.getColor(getContext(), R.color.red_500); // Or a dimmer red if preferred
        } else {
            highlightColor = ContextCompat.getColor(getContext(), R.color.teal_200);
            secondaryColor = ContextCompat.getColor(getContext(), R.color.secondary_text);
        }

        bubblePaint.setColor(highlightColor);
        bubblePaint.setAlpha(200);
        barBubblePaint.setColor(highlightColor);
        barBubblePaint.setAlpha(200);
        
        // Update other paints for night mode
        circlePaint.setColor(secondaryColor);
        barPaint.setColor(secondaryColor);
        linePaint.setColor(secondaryColor);
        // Keep alpha for linePaint
        linePaint.setAlpha(150); 
        
        centerLinePaint.setColor(secondaryColor);
        centerLinePaint.setAlpha(200);

        invalidate();
    }

    /**
     * Set tilt where x and y are in range -1..1
     */
    public void setTilt(float x, float y) {
        tiltX = x;
        tiltY = y;
        invalidate(); // redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        // Clamp tilt -1..1
        float clampedX = Math.max(-1f, Math.min(1f, tiltX));
        float clampedY = Math.max(-1f, Math.min(1f, tiltY));
        float displayX = hyperbolicScale(clampedX);
        float displayY = hyperbolicScale(clampedY);

        // ---- CIRCULAR LEVEL (upper-left) ----
        float circleCx = width * 0.30f;
        float circleCy = height * 0.40f;
        float circleRadius = Math.min(width, height) * 0.22f;
        float circleBubbleRadius = circleRadius / 5f;

        // Outer circle
        canvas.drawCircle(circleCx, circleCy, circleRadius, circlePaint);

        // Concentric guide lines
        for (int i = 1; i <= 3; i++) {
            canvas.drawCircle(circleCx, circleCy, circleRadius * (i / 4.0f), linePaint);
        }

        // Crosshair for circular level
        canvas.drawLine(circleCx - circleRadius, circleCy, circleCx + circleRadius, circleCy, centerLinePaint);
        canvas.drawLine(circleCx, circleCy - circleRadius, circleCx, circleCy + circleRadius, centerLinePaint);

        float circleMaxOffset = circleRadius - circleBubbleRadius;

        // Bubble moves to the highest point (opposite of tilt)
        float circleBubbleCx = circleCx + displayX * circleMaxOffset;
        float circleBubbleCy = circleCy - displayY * circleMaxOffset;

        canvas.drawCircle(circleBubbleCx, circleBubbleCy, circleBubbleRadius, bubblePaint);

        // ---- VERTICAL BAR LEVEL (next to circle, uses tiltY) ----
        float vBarHeight = circleRadius * 2.0f;
        float vBarWidth = vBarHeight * 0.25f;

        float vBarCenterX = width * 0.75f;
        float vBarCenterY = circleCy;

        float vBarLeft = vBarCenterX - vBarWidth / 2f;
        float vBarRight = vBarCenterX + vBarWidth / 2f;
        float vBarTop = vBarCenterY - vBarHeight / 2f;
        float vBarBottom = vBarCenterY + vBarHeight / 2f;

        vBarRect.set(vBarLeft, vBarTop, vBarRight, vBarBottom);
        float vBarRadius = vBarWidth / 2f;

        // Bar outline
        canvas.drawRoundRect(vBarRect, vBarRadius, vBarRadius, barPaint);

        // Center line for vertical bar
        canvas.drawLine(vBarLeft, vBarCenterY, vBarRight, vBarCenterY, centerLinePaint);

        // Guide lines for vertical bar
        for (int i = 1; i <= 2; i++) {
            float y = vBarCenterY - (vBarHeight / 2.0f) * (i / 3.0f);
            canvas.drawLine(vBarLeft, y, vBarRight, y, linePaint);
            y = vBarCenterY + (vBarHeight / 2.0f) * (i / 3.0f);
            canvas.drawLine(vBarLeft, y, vBarRight, y, linePaint);
        }

        float vBarBubbleRadius = vBarWidth / 2.5f;
        float vBarMaxOffset = (vBarHeight / 2f) - vBarBubbleRadius - 6f;

        // Bubble moves to the highest point (opposite of tilt)
        float vBarBubbleCx = vBarCenterX;
        float vBarBubbleCy = vBarCenterY - displayY * vBarMaxOffset;

        canvas.drawCircle(vBarBubbleCx, vBarBubbleCy, vBarBubbleRadius, barBubblePaint);

        // ---- HORIZONTAL BAR LEVEL (bottom, uses tiltX) ----
        float hBarWidth = width * 0.8f;
        float hBarHeight = height * 0.10f;
        float hBarLeft = (width - hBarWidth) / 2f;
        float hBarRight = hBarLeft + hBarWidth;

        float hBarCenterY = height * 0.80f;
        float hBarTop = hBarCenterY - hBarHeight / 2f;
        float hBarBottom = hBarCenterY + hBarHeight / 2f;

        hBarRect.set(hBarLeft, hBarTop, hBarRight, hBarBottom);
        float hBarRadius = hBarHeight / 2f;

        // Bar outline
        canvas.drawRoundRect(hBarRect, hBarRadius, hBarRadius, barPaint);

        float hBarCenterX = width / 2f;

        // Center line for horizontal bar
        canvas.drawLine(hBarCenterX, hBarTop, hBarCenterX, hBarBottom, centerLinePaint);

        // Guide lines for horizontal bar
        for (int i = 1; i <= 4; i++) {
            float x = hBarCenterX - (hBarWidth / 2.0f) * (i / 5.0f);
            canvas.drawLine(x, hBarTop, x, hBarBottom, linePaint);
            x = hBarCenterX + (hBarWidth / 2.0f) * (i / 5.0f);
            canvas.drawLine(x, hBarTop, x, hBarBottom, linePaint);
        }

        float hBarBubbleRadius = hBarHeight / 2.5f;
        float hBarMaxOffset = (hBarWidth / 2f) - hBarBubbleRadius - 8f;

        // Bubble moves to the highest point (opposite of tilt)
        float hBarBubbleCx = hBarCenterX + displayX * hBarMaxOffset;
        float hBarBubbleCy = hBarCenterY;

        canvas.drawCircle(hBarBubbleCx, hBarBubbleCy, hBarBubbleRadius, barBubblePaint);
    }

    private float hyperbolicScale(float value) {
        float scaled = (float) Math.tanh(HYPERBOLIC_GAIN * value);
        return scaled / HYPERBOLIC_NORMALIZER;
    }
}
