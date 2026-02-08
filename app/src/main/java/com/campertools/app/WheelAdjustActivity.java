package com.campertools.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class WheelAdjustActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS = "campertools_prefs";
    private static final String PREF_WHEELBASE = "pref_wheelbase";
    private static final String PREF_TRACK_WIDTH = "pref_track_width";
    private static final float ALPHA = 0.12f; // Filter factor

    private EditText inputWheelbase;
    private EditText inputTrackWidth;
    private TextView textFL, textFR, textBL, textBR;
    private TextView textFrontLabel, textHeader, textClose;
    private TextView labelWheelbase, labelTrackWidth;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final float[] gravity = new float[3];
    private final float[] filteredGravity = new float[3];
    private boolean hasGravity = false;

    private float pitchOffsetDeg = 0f;
    private float rollOffsetDeg = 0f;
    private boolean useImperial = false;
    private boolean useNightMode = false;

    private float wheelbase = 0f;
    private float trackWidth = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_wheel_adjust);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        inputWheelbase = findViewById(R.id.inputWheelbase);
        inputTrackWidth = findViewById(R.id.inputTrackWidth);
        textFL = findViewById(R.id.textFL);
        textFR = findViewById(R.id.textFR);
        textBL = findViewById(R.id.textBL);
        textBR = findViewById(R.id.textBR);
        textFrontLabel = findViewById(R.id.textFrontLabel);
        textHeader = findViewById(R.id.textHeader);
        textClose = findViewById(R.id.textClose);
        labelWheelbase = findViewById(R.id.labelWheelbase);
        labelTrackWidth = findViewById(R.id.labelTrackWidth);

        textClose.setOnClickListener(v -> finish());

        // Load Intent Extras
        if (getIntent() != null) {
            pitchOffsetDeg = getIntent().getFloatExtra(SettingsActivity.EXTRA_PITCH_OFFSET_DEG, 0f);
            rollOffsetDeg = getIntent().getFloatExtra(SettingsActivity.EXTRA_ROLL_OFFSET_DEG, 0f);
            useImperial = getIntent().getBooleanExtra(SettingsActivity.EXTRA_USE_IMPERIAL, false);
            useNightMode = getIntent().getBooleanExtra(SettingsActivity.EXTRA_USE_NIGHT_MODE, false);
        }

        // Load Prefs
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        wheelbase = prefs.getFloat(PREF_WHEELBASE, 0f);
        trackWidth = prefs.getFloat(PREF_TRACK_WIDTH, 0f);

        if (wheelbase > 0) inputWheelbase.setText(String.format(Locale.US, "%.1f", wheelbase));
        if (trackWidth > 0) inputTrackWidth.setText(String.format(Locale.US, "%.1f", trackWidth));

        // Listeners for inputs
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                saveDimensions();
                calculateAdjustments();
            }
        };
        inputWheelbase.addTextChangedListener(watcher);
        inputTrackWidth.addTextChangedListener(watcher);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        applyNightMode();
        applyUnits();
    }

    private void applyUnits() {
        if (useImperial) {
            labelWheelbase.setText("Wheelbase (inches)");
            labelTrackWidth.setText("Track Width (inches)");
        } else {
            labelWheelbase.setText("Wheelbase (mm)");
            labelTrackWidth.setText("Track Width (mm)");
        }
    }

    private void saveDimensions() {
        try {
            String wbStr = inputWheelbase.getText().toString();
            String twStr = inputTrackWidth.getText().toString();
            if (!wbStr.isEmpty()) wheelbase = Float.parseFloat(wbStr);
            if (!twStr.isEmpty()) trackWidth = Float.parseFloat(twStr);

            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putFloat(PREF_WHEELBASE, wheelbase)
                    .putFloat(PREF_TRACK_WIDTH, trackWidth)
                    .apply();
        } catch (NumberFormatException e) {
            // Ignore
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!hasGravity) {
                System.arraycopy(event.values, 0, filteredGravity, 0, 3);
                hasGravity = true;
            } else {
                // Low-pass filter
                filteredGravity[0] += ALPHA * (event.values[0] - filteredGravity[0]);
                filteredGravity[1] += ALPHA * (event.values[1] - filteredGravity[1]);
                filteredGravity[2] += ALPHA * (event.values[2] - filteredGravity[2]);
            }
            System.arraycopy(filteredGravity, 0, gravity, 0, 3);
            calculateAdjustments();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void calculateAdjustments() {
        if (!hasGravity) return;

        float g = SensorManager.GRAVITY_EARTH;
        float normX = gravity[0] / g;
        float normY = gravity[1] / g;

        if (normX > 1f) normX = 1f;
        if (normX < -1f) normX = -1f;
        if (normY > 1f) normY = 1f;
        if (normY < -1f) normY = -1f;

        // Apply offsets (same logic as MainActivity)
        float offsetPitchNorm = (float) Math.sin(Math.toRadians(pitchOffsetDeg));
        float offsetRollNorm = (float) Math.sin(Math.toRadians(rollOffsetDeg));

        float adjustedX = clampUnit(normX - offsetRollNorm);
        float adjustedY = clampUnit(normY - offsetPitchNorm);

        // Calculate pitch and roll angles in radians
        // Note: MainActivity uses:
        // double pitchDeg = -Math.asin(adjustedY) * 180.0 / Math.PI;
        // double rollDeg = Math.asin(adjustedX) * 180.0 / Math.PI;
        
        // We use radians for calc
        double pitchRad = -Math.asin(adjustedY);
        double rollRad = Math.asin(adjustedX);

        // Heights relative to center (0,0)
        // Pitch: Positive Pitch (Front Up?) -> Wait.
        // MainActivity: -asin(y). If phone top tilts up, y is positive? No, standard Android: Y points UP.
        // If top tilts back (screen up), Y is positive.
        // Let's verify standard orientation:
        // Lying flat: Z=9.8.
        // Tilt top up (pitch up): Y increases (positive).
        // MainActivity pitchDeg = -asin(Y). So Positive Y -> Negative PitchDeg.
        // Negative PitchDeg -> Nose Down?
        // Standard convention: Pitch Up is positive.
        // So if Top tilts Up, PitchDeg is negative. That seems inverted relative to standard "Nose Up".
        // BUT, let's stick to the visual:
        // If Top tilts UP, Y > 0. PitchDeg < 0.
        // If Top tilts UP, the Front Wheels are HIGHER.
        // So Front Height should be Positive.
        
        // Front Height = (WB/2) * sin(-pitchRad)?
        // If PitchDeg < 0 (Top Up), we want Front Height > 0.
        // sin(PitchDeg) is negative.
        // So we need -(WB/2) * sin(PitchDeg).
        // Or simply: (WB/2) * sin(AngleOfElevation).
        // AngleOfElevation of phone top = asin(adjustedY).
        // So Front Height ~ adjustedY.
        
        // Let's verify:
        // Top tilts UP -> adjustedY > 0. Front is HIGH.
        // Height_Front = (wheelbase / 2.0) * (adjustedY); (approx sin theta = Y/g)
        // Height_Rear = -(wheelbase / 2.0) * (adjustedY);
        
        // Roll:
        // Tilt Right side down -> X decreases?
        // Standard: X points Right.
        // Tilt Right Down -> X < 0.
        // MainActivity rollDeg = asin(X). So X < 0 -> Roll < 0.
        // If Right is Down, Right Height should be Negative.
        // Height_Right = (trackWidth / 2.0) * (adjustedX); (since X < 0 -> Height < 0).
        // Height_Left = -(trackWidth / 2.0) * (adjustedX);
        
        // Using actual sin(asin(val)) = val. So we can just use adjustedX/Y directly if we assume they are sine components.
        // Yes, adjustedX IS the sine of the angle (normalized gravity component).
        
        double hFront = (wheelbase / 2.0) * adjustedY; // Positive if Top Up
        double hRear = -(wheelbase / 2.0) * adjustedY;
        
        double hRight = (trackWidth / 2.0) * adjustedX; // Positive if Right Up
        double hLeft = -(trackWidth / 2.0) * adjustedX;

        // Corners
        double hFL = hFront + hLeft;
        double hFR = hFront + hRight;
        double hBL = hRear + hLeft;
        double hBR = hRear + hRight;

        // We want to raise the low ones to match the highest one.
        double maxH = Math.max(Math.max(hFL, hFR), Math.max(hBL, hBR));

        double shimFL = maxH - hFL;
        double shimFR = maxH - hFR;
        double shimBL = maxH - hBL;
        double shimBR = maxH - hBR;

        updateWheelText(textFL, shimFL);
        updateWheelText(textFR, shimFR);
        updateWheelText(textBL, shimBL);
        updateWheelText(textBR, shimBR);
    }

    private void updateWheelText(TextView view, double val) {
        // Val is in same units as dimensions (mm or inches)
        // If using Imperial, dimensions are inches -> val is inches.
        // If Metric, dimensions are mm -> val is mm.
        if (useImperial) {
            view.setText(String.format(Locale.US, "%.1f\"", val));
        } else {
            view.setText(String.format(Locale.US, "%.0f mm", val));
        }
    }

    private float clampUnit(float value) {
        if (value > 1f) return 1f;
        if (value < -1f) return -1f;
        return value;
    }

    private void applyNightMode() {
        int textColor;
        int backgroundColor;
        int hintColor;

        if (useNightMode) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.01f;
            getWindow().setAttributes(layout);
            
            textColor = ContextCompat.getColor(this, R.color.red_500);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
            hintColor = ContextCompat.getColor(this, R.color.red_500);
        } else {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(layout);

            textColor = ContextCompat.getColor(this, R.color.primary_text);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
            hintColor = ContextCompat.getColor(this, R.color.secondary_text);
        }
        
        findViewById(android.R.id.content).setBackgroundColor(backgroundColor);

        if (textHeader != null) textHeader.setTextColor(textColor);
        if (textFrontLabel != null) textFrontLabel.setTextColor(textColor);
        if (labelWheelbase != null) labelWheelbase.setTextColor(textColor);
        if (labelTrackWidth != null) labelTrackWidth.setTextColor(textColor);
        
        if (inputWheelbase != null) {
            inputWheelbase.setTextColor(textColor);
            inputWheelbase.setHintTextColor(hintColor);
        }
        if (inputTrackWidth != null) {
            inputTrackWidth.setTextColor(textColor);
            inputTrackWidth.setHintTextColor(hintColor);
        }
        
        if (textFL != null) textFL.setTextColor(textColor);
        if (textFR != null) textFR.setTextColor(textColor);
        if (textBL != null) textBL.setTextColor(textColor);
        if (textBR != null) textBR.setTextColor(textColor);
        
        if (textClose != null) textClose.setTextColor(useNightMode ? textColor : ContextCompat.getColor(this, R.color.teal_200));
    }
}
