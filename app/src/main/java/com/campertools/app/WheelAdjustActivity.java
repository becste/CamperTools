package com.campertools.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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

    private EditText inputWheelbase;
    private EditText inputTrackWidth;
    private TextView textFLValue, textFRValue, textBLValue, textBRValue;
    private TextView labelFL, labelFR, labelBL, labelBR;
    private TextView textFrontLabel, textHeader, textClose, textPhoneTopLabel;
    private TextView labelWheelbase, labelTrackWidth;
    private TextView textChassisFront, textChassisArrow;
    private Button buttonRecalculate;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float pitchOffsetDeg = 0f;
    private float rollOffsetDeg = 0f;
    private boolean useImperial = false;
    private boolean useNightMode = false;

    private float wheelbase = 0f;
    private float trackWidth = 0f;

    // Measurement State
    private boolean isMeasuring = false;
    private double accX = 0;
    private double accY = 0;
    private int sampleCount = 0;
    
    // Locked Values (Normalized -1..1)
    private float lockedNormX = 0f;
    private float lockedNormY = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_wheel_adjust);

        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left + padding, insets.top + padding, insets.right + padding, insets.bottom + padding);
            return WindowInsetsCompat.CONSUMED;
        });

        inputWheelbase = findViewById(R.id.inputWheelbase);
        inputTrackWidth = findViewById(R.id.inputTrackWidth);
        
        textFLValue = findViewById(R.id.textFLValue);
        textFRValue = findViewById(R.id.textFRValue);
        textBLValue = findViewById(R.id.textBLValue);
        textBRValue = findViewById(R.id.textBRValue);
        
        labelFL = findViewById(R.id.labelFL);
        labelFR = findViewById(R.id.labelFR);
        labelBL = findViewById(R.id.labelBL);
        labelBR = findViewById(R.id.labelBR);

        textChassisFront = findViewById(R.id.textChassisFront);
        textChassisArrow = findViewById(R.id.textChassisArrow);
        textPhoneTopLabel = findViewById(R.id.textPhoneTopLabel);
        
        textHeader = findViewById(R.id.textHeader);
        textClose = findViewById(R.id.textClose);
        labelWheelbase = findViewById(R.id.labelWheelbase);
        labelTrackWidth = findViewById(R.id.labelTrackWidth);
        buttonRecalculate = findViewById(R.id.buttonRecalculate);

        textClose.setOnClickListener(v -> finish());
        buttonRecalculate.setOnClickListener(v -> startMeasurement());

        // Load Intent Extras
        if (getIntent() != null) {
            pitchOffsetDeg = getIntent().getFloatExtra(SettingsActivity.EXTRA_PITCH_OFFSET_DEG, 0f);
            rollOffsetDeg = getIntent().getFloatExtra(SettingsActivity.EXTRA_ROLL_OFFSET_DEG, 0f);
            useImperial = getIntent().getBooleanExtra(SettingsActivity.EXTRA_USE_IMPERIAL, false);
            useNightMode = getIntent().getBooleanExtra(SettingsActivity.EXTRA_USE_NIGHT_MODE, false);
            lockedNormX = getIntent().getFloatExtra("EXTRA_START_NORM_X", 0f);
            lockedNormY = getIntent().getFloatExtra("EXTRA_START_NORM_Y", 0f);
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
                calculateAdjustments(lockedNormX, lockedNormY);
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
        
        // Initial Calc
        calculateAdjustments(lockedNormX, lockedNormY);
    }

    private void startMeasurement() {
        if (isMeasuring) return;
        isMeasuring = true;
        buttonRecalculate.setEnabled(false);
        buttonRecalculate.setText("Measuring...");
        
        accX = 0;
        accY = 0;
        sampleCount = 0;
        
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        
        new Handler(Looper.getMainLooper()).postDelayed(this::stopMeasurement, 2000);
    }

    private void stopMeasurement() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        
        if (sampleCount > 0) {
            // Average acceleration
            double avgX = accX / sampleCount;
            double avgY = accY / sampleCount;
            
            // Normalize
            lockedNormX = (float) (avgX / SensorManager.GRAVITY_EARTH);
            lockedNormY = (float) (avgY / SensorManager.GRAVITY_EARTH);
            
            calculateAdjustments(lockedNormX, lockedNormY);
        }
        
        isMeasuring = false;
        buttonRecalculate.setEnabled(true);
        buttonRecalculate.setText(R.string.recalculate_button);
    }

    private void applyUnits() {
        if (useImperial) {
            labelWheelbase.setText("Wheelbase (inches)");
            labelTrackWidth.setText("Track Width (inches)");
        } else {
            labelWheelbase.setText("Wheelbase (cm)");
            labelTrackWidth.setText("Track Width (cm)");
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
        // No automatic sensor registration
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        isMeasuring = false;
        buttonRecalculate.setEnabled(true);
        buttonRecalculate.setText(R.string.recalculate_button);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accX += event.values[0];
            accY += event.values[1];
            sampleCount++;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void calculateAdjustments(float normX, float normY) {
        if (normX > 1f) normX = 1f;
        if (normX < -1f) normX = -1f;
        if (normY > 1f) normY = 1f;
        if (normY < -1f) normY = -1f;

        // Apply offsets
        float offsetPitchNorm = (float) Math.sin(Math.toRadians(pitchOffsetDeg));
        float offsetRollNorm = (float) Math.sin(Math.toRadians(rollOffsetDeg));

        float adjustedX = clampUnit(normX - offsetRollNorm);
        float adjustedY = clampUnit(normY - offsetPitchNorm);

        // Height Calculation
        double hFront = (wheelbase / 2.0) * adjustedY;
        double hRear = -(wheelbase / 2.0) * adjustedY;
        
        double hRight = (trackWidth / 2.0) * adjustedX;
        double hLeft = -(trackWidth / 2.0) * adjustedX;

        // Corners
        double hFL = hFront + hLeft;
        double hFR = hFront + hRight;
        double hBL = hRear + hLeft;
        double hBR = hRear + hRight;

        // Shim = difference from MAX
        double maxH = Math.max(Math.max(hFL, hFR), Math.max(hBL, hBR));

        double shimFL = maxH - hFL;
        double shimFR = maxH - hFR;
        double shimBL = maxH - hBL;
        double shimBR = maxH - hBR;

        updateWheelText(textFLValue, shimFL);
        updateWheelText(textFRValue, shimFR);
        updateWheelText(textBLValue, shimBL);
        updateWheelText(textBRValue, shimBR);
    }

    private void updateWheelText(TextView view, double val) {
        if (useImperial) {
            view.setText(String.format(Locale.US, "%.1f\"", val));
        } else {
            view.setText(String.format(Locale.US, "%.1f cm", val));
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
        int highlightColor;

        if (useNightMode) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.01f;
            getWindow().setAttributes(layout);
            
            textColor = ContextCompat.getColor(this, R.color.red_500);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
            hintColor = ContextCompat.getColor(this, R.color.red_500);
            highlightColor = textColor;
        } else {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(layout);

            textColor = ContextCompat.getColor(this, R.color.primary_text);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
            hintColor = ContextCompat.getColor(this, R.color.secondary_text);
            highlightColor = ContextCompat.getColor(this, R.color.teal_200);
        }
        
        findViewById(android.R.id.content).setBackgroundColor(backgroundColor);

        if (textHeader != null) textHeader.setTextColor(textColor);
        if (textFrontLabel != null) textFrontLabel.setTextColor(textColor);
        if (textPhoneTopLabel != null) textPhoneTopLabel.setTextColor(textColor);
        if (labelWheelbase != null) labelWheelbase.setTextColor(textColor);
        
        if (inputWheelbase != null) {
            inputWheelbase.setTextColor(textColor);
            inputWheelbase.setHintTextColor(hintColor);
        }
        if (inputTrackWidth != null) {
            inputTrackWidth.setTextColor(textColor);
            inputTrackWidth.setHintTextColor(hintColor);
        }
        
        if (labelFL != null) labelFL.setTextColor(textColor);
        if (labelFR != null) labelFR.setTextColor(textColor);
        if (labelBL != null) labelBL.setTextColor(textColor);
        if (labelBR != null) labelBR.setTextColor(textColor);

        if (textFLValue != null) textFLValue.setTextColor(highlightColor);
        if (textFRValue != null) textFRValue.setTextColor(highlightColor);
        if (textBLValue != null) textBLValue.setTextColor(highlightColor);
        if (textBRValue != null) textBRValue.setTextColor(highlightColor);

        if (textChassisFront != null) textChassisFront.setTextColor(useNightMode ? textColor : ContextCompat.getColor(this, R.color.secondary_text));
        if (textChassisArrow != null) textChassisArrow.setTextColor(useNightMode ? textColor : ContextCompat.getColor(this, R.color.secondary_text));
        
        if (textClose != null) textClose.setTextColor(useNightMode ? textColor : ContextCompat.getColor(this, R.color.teal_200));
        
        if (buttonRecalculate != null) {
            buttonRecalculate.setTextColor(useNightMode ? textColor : ContextCompat.getColor(this, android.R.color.white));
        }
    }
}