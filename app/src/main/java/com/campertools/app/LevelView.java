package com.campertools.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class LevelView extends View {

    // Normalized tilt values in range -1..1
    private float tiltX = 0f;  // left/right
    private float tiltY = 0f;  // up/down

    private Paint circlePaint;
    private Paint bubblePaint;
    private Paint barPaint;
    private Paint barBubblePaint;
    private Paint linePaint;

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
        int primaryColor = ContextCompat.getColor(context, R.color.teal_200); // Use accent color for bubbles

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(6f);
        circlePaint.setColor(secondaryColor);

        bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(primaryColor);

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(6f);
        barPaint.setColor(secondaryColor);

        barBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBubblePaint.setStyle(Paint.Style.FILL);
        barBubblePaint.setColor(primaryColor);
        
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);
        linePaint.setColor(secondaryColor);
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

        // ---- CIRCULAR LEVEL (upper-left) ----
        float circleCx = width * 0.30f;
        float circleCy = height * 0.35f;
        float circleRadius = Math.min(width, height) * 0.22f;
        float circleBubbleRadius = circleRadius / 6f;

        // Outer circle
        canvas.drawCircle(circleCx, circleCy, circleRadius, circlePaint);
        
        // Crosshair for circular level
        canvas.drawLine(circleCx - circleRadius, circleCy, circleCx + circleRadius, circleCy, linePaint);
        canvas.drawLine(circleCx, circleCy - circleRadius, circleCx, circleCy + circleRadius, linePaint);

        float circleMaxOffset = circleRadius - circleBubbleRadius;

        // Bubble moves opposite to tilt on X, same on Y
        float circleBubbleCx = circleCx - clampedX * circleMaxOffset;
        float circleBubbleCy = circleCy + clampedY * circleMaxOffset;

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

        RectF vBarRect = new RectF(vBarLeft, vBarTop, vBarRight, vBarBottom);
        float vBarRadius = vBarWidth / 2f;

        // Bar outline
        canvas.drawRoundRect(vBarRect, vBarRadius, vBarRadius, barPaint);
        
        // Center line for vertical bar
        canvas.drawLine(vBarLeft, vBarCenterY, vBarRight, vBarCenterY, linePaint);

        float vBarBubbleRadius = vBarWidth / 2.5f;
        float vBarMaxOffset = (vBarHeight / 2f) - vBarBubbleRadius - 6f;

        // Bubble moves with tiltY (up/down)
        float vBarBubbleCx = vBarCenterX;
        float vBarBubbleCy = vBarCenterY + clampedY * vBarMaxOffset;

        canvas.drawCircle(vBarBubbleCx, vBarBubbleCy, vBarBubbleRadius, barBubblePaint);

        // ---- HORIZONTAL BAR LEVEL (bottom, uses tiltX) ----
        float hBarWidth = width * 0.8f;
        float hBarHeight = height * 0.10f;
        float hBarLeft = (width - hBarWidth) / 2f;
        float hBarRight = hBarLeft + hBarWidth;

        float hBarCenterY = height * 0.80f;
        float hBarTop = hBarCenterY - hBarHeight / 2f;
        float hBarBottom = hBarCenterY + hBarHeight / 2f;

        RectF hBarRect = new RectF(hBarLeft, hBarTop, hBarRight, hBarBottom);
        float hBarRadius = hBarHeight / 2f;

        // Bar outline
        canvas.drawRoundRect(hBarRect, hBarRadius, hBarRadius, barPaint);
        
        float hBarCenterX = width / 2f;

        // Center line for horizontal bar
        canvas.drawLine(hBarCenterX, hBarTop, hBarCenterX, hBarBottom, linePaint);

        float hBarBubbleRadius = hBarHeight / 2.5f;
        float hBarMaxOffset = (hBarWidth / 2f) - hBarBubbleRadius - 8f;

        // Bubble moves with tiltX (left/right)
        float hBarBubbleCx = hBarCenterX - clampedX * hBarMaxOffset;
        float hBarBubbleCy = hBarCenterY;

        canvas.drawCircle(hBarBubbleCx, hBarBubbleCy, hBarBubbleRadius, barBubblePaint);
    }
}