package com.campertools.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

public class CompassView extends View {

    private float direction = 0;

    private Paint circlePaint;
    private Paint northPaint;
    private Paint southPaint;
    private Paint textPaint;
    private Path northPath;
    private Path southPath;
    private float centerX;
    private float centerY;
    private float radius;

    public CompassView(Context context) {
        super(context);
        init(context);
    }

    public CompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CompassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        int secondaryColor = ContextCompat.getColor(context, R.color.secondary_text);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(6f);
        circlePaint.setColor(secondaryColor);

        northPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        northPaint.setStyle(Paint.Style.FILL);
        northPaint.setColor(Color.RED);

        southPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        southPaint.setStyle(Paint.Style.FILL);
        southPaint.setColor(secondaryColor);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(secondaryColor);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);

        northPath = new Path();
        southPath = new Path();
    }

    public void setNightMode(boolean isNightMode) {
        int color;
        if (isNightMode) {
            color = ContextCompat.getColor(getContext(), R.color.red_500);
        } else {
            color = ContextCompat.getColor(getContext(), R.color.secondary_text);
        }

        circlePaint.setColor(color);
        southPaint.setColor(color);
        textPaint.setColor(color);
        
        invalidate();
    }

    /**
     * Set compass direction in degrees (0 = North)
     */
    public void setDirection(float direction) {
        this.direction = direction;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f * 0.8f;

        northPath.reset();
        northPath.moveTo(centerX, centerY - radius);
        northPath.lineTo(centerX - 20, centerY);
        northPath.lineTo(centerX + 20, centerY);
        northPath.close();

        southPath.reset();
        southPath.moveTo(centerX, centerY + radius);
        southPath.lineTo(centerX - 20, centerY);
        southPath.lineTo(centerX + 20, centerY);
        southPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the outer circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        // Save the current canvas state
        canvas.save();

        // Rotate the canvas to the correct direction
        canvas.rotate(-direction, centerX, centerY);

        // Draw the North-pointing triangle
        canvas.drawPath(northPath, northPaint);

        // Draw the South-pointing triangle
        canvas.drawPath(southPath, southPaint);

        // Draw Cardinal Direction Letters
        canvas.drawText("N", centerX, centerY - radius - 20, textPaint);
        canvas.drawText("S", centerX, centerY + radius + 50, textPaint);
        canvas.drawText("E", centerX + radius + 30, centerY + 15, textPaint);
        canvas.drawText("W", centerX - radius - 30, centerY + 15, textPaint);

        // Restore the canvas to its original state (pre-rotation)
        canvas.restore();
    }
}
