package com.campertools.app;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity implements SensorEventListener {

    public static final String EXTRA_PITCH_OFFSET_DEG = "pitchOffsetDeg";
    public static final String EXTRA_ROLL_OFFSET_DEG = "rollOffsetDeg";
    public static final String EXTRA_USE_IMPERIAL = "useImperial";
    public static final String EXTRA_USE_NIGHT_MODE = "useNightMode";

    private static final float ALPHA = 0.1f; // Filter factor

    private TextView textLevelHeader;
    private TextView textPitchLabel;
    private TextView textRollLabel;
    private TextView textUnitsHeader;
    private TextView textHelpLink; // Add
    private EditText inputPitchOffset;
    private EditText inputRollOffset;
    private Button buttonAutoCalibrate;
    private SwitchMaterial switchUnits;
    private SwitchMaterial switchNightMode;
    private TextView buttonBack; // Changed from Button
    private boolean useImperial = false;
    private boolean useNightMode = false;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final float[] gravity = new float[3];
    private boolean hasGravity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left + padding, insets.top + padding, insets.right + padding, insets.bottom + padding);
            return WindowInsetsCompat.CONSUMED;
        });

        textLevelHeader = findViewById(R.id.textLevelHeader);
        textPitchLabel = findViewById(R.id.textPitchLabel);
        textRollLabel = findViewById(R.id.textRollLabel);
        textUnitsHeader = findViewById(R.id.textUnitsHeader);
        inputPitchOffset = findViewById(R.id.inputPitchOffset);
        inputRollOffset = findViewById(R.id.inputRollOffset);
        buttonAutoCalibrate = findViewById(R.id.buttonAutoCalibrate);
        switchUnits = findViewById(R.id.switchUnits);
        switchNightMode = findViewById(R.id.switchNightMode);
        buttonBack = findViewById(R.id.buttonBack);
        textHelpLink = findViewById(R.id.textHelpLink);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        Intent intent = getIntent();
        float pitchDeg = intent.getFloatExtra(EXTRA_PITCH_OFFSET_DEG, 0f);
        float rollDeg = intent.getFloatExtra(EXTRA_ROLL_OFFSET_DEG, 0f);
        useImperial = intent.getBooleanExtra(EXTRA_USE_IMPERIAL, false);
        useNightMode = intent.getBooleanExtra(EXTRA_USE_NIGHT_MODE, false);

        // Populate UI
        inputPitchOffset.setText(String.format(Locale.getDefault(), "%.1f", pitchDeg));
        inputRollOffset.setText(String.format(Locale.getDefault(), "%.1f", rollDeg));
        
        switchUnits.setChecked(useImperial);

        switchUnits.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useImperial = isChecked;
        });

        if (switchNightMode != null) {
            switchNightMode.setChecked(useNightMode);
            switchNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                useNightMode = isChecked;
                applyNightMode();
            });
        }

        if (buttonBack != null) {
            buttonBack.setOnClickListener(v -> {
                saveSettings();
                finish();
            });
        }
        
        if (textHelpLink != null) {
            textHelpLink.setOnClickListener(v -> {
                startActivity(new Intent(this, HelpActivity.class));
            });
        }

        if (buttonAutoCalibrate != null) {
            buttonAutoCalibrate.setOnClickListener(v -> performAutoCalibration());
        }

        applyNightMode();
    }

    private void applyNightMode() {
        int textColor;
        int backgroundColor;
        int hintColor;

        if (useNightMode) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.01f; // Dim the screen
            getWindow().setAttributes(layout);
            
            textColor = ContextCompat.getColor(this, R.color.red_500);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
            hintColor = ContextCompat.getColor(this, R.color.red_500); // Or slightly dimmer
        } else {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(layout);

            textColor = ContextCompat.getColor(this, R.color.primary_text);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
            hintColor = ContextCompat.getColor(this, R.color.secondary_text);
        }
        
        findViewById(android.R.id.content).setBackgroundColor(backgroundColor);

        if (textLevelHeader != null) textLevelHeader.setTextColor(textColor);
        if (textPitchLabel != null) textPitchLabel.setTextColor(textColor);
        if (textRollLabel != null) textRollLabel.setTextColor(textColor);
        if (textUnitsHeader != null) textUnitsHeader.setTextColor(textColor);
        
        if (inputPitchOffset != null) {
            inputPitchOffset.setTextColor(textColor);
            inputPitchOffset.setHintTextColor(hintColor);
        }
        if (inputRollOffset != null) {
            inputRollOffset.setTextColor(textColor);
            inputRollOffset.setHintTextColor(hintColor);
        }
        
        if (switchUnits != null) switchUnits.setTextColor(textColor);
        if (switchNightMode != null) switchNightMode.setTextColor(textColor);
        
        if (buttonAutoCalibrate != null) buttonAutoCalibrate.setTextColor(useNightMode ? textColor : ContextCompat.getColor(this, android.R.color.white));
        
        int linkColor = useNightMode ? textColor : ContextCompat.getColor(this, R.color.teal_200);
        if (buttonBack != null) buttonBack.setTextColor(linkColor);
        if (textHelpLink != null) textHelpLink.setTextColor(linkColor);
    }

    private void saveSettings() {
        double pitchVal = parseDoubleSafe(inputPitchOffset.getText().toString());
        double rollVal = parseDoubleSafe(inputRollOffset.getText().toString());

        Intent result = new Intent();
        result.putExtra(EXTRA_PITCH_OFFSET_DEG, (float) pitchVal);
        result.putExtra(EXTRA_ROLL_OFFSET_DEG, (float) rollVal);
        result.putExtra(EXTRA_USE_IMPERIAL, useImperial);
        result.putExtra(EXTRA_USE_NIGHT_MODE, useNightMode);
        setResult(RESULT_OK, result);
    }
    
    private double parseDoubleSafe(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
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
                System.arraycopy(event.values, 0, gravity, 0, 3);
                hasGravity = true;
            } else {
                // Low-pass filter
                gravity[0] = ALPHA * event.values[0] + (1 - ALPHA) * gravity[0];
                gravity[1] = ALPHA * event.values[1] + (1 - ALPHA) * gravity[1];
                gravity[2] = ALPHA * event.values[2] + (1 - ALPHA) * gravity[2];
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    private void performAutoCalibration() {
        if (!hasGravity) {
            Toast.makeText(this, "Waiting for sensor data...", Toast.LENGTH_SHORT).show();
            return;
        }

        float g = SensorManager.GRAVITY_EARTH;
        float normX = gravity[0] / g; // Roll component
        float normY = gravity[1] / g; // Pitch component

        // Clamp
        if (normX > 1f) normX = 1f;
        if (normX < -1f) normX = -1f;
        if (normY > 1f) normY = 1f;
        if (normY < -1f) normY = -1f;

        // Calculate angle in degrees
        // pitch = -asin(normY) in MainActivity logic, so we offset by that amount?
        // If current reading is +5 deg, we want offset to be +5 deg so result is 0.
        
        // MainActivity: double pitchDeg = -Math.asin(adjustedY) * 180.0 / Math.PI;
        // adjustedY = normY - offset;
        // We want adjustedY = 0 => offset = normY.
        // So offset_sine = normY.
        // offset_deg = asin(normY) * 180 / PI.
        // BUT wait, MainActivity pitch is NEGATIVE asin.
        // Let's stick to the convention: "Pitch Offset" means the value we subtract from the reading?
        // Or the value of the current tilt?
        // If we want "Auto Calibrate", we want the current tilt to become "Zero".
        // Current Pitch (deg) = -asin(normY) * 180/PI.
        // If we want this to be the new zero, we should store this value as the offset.
        
        double pitchDeg = -Math.asin(normY) * 180.0 / Math.PI;
        double rollDeg = Math.asin(normX) * 180.0 / Math.PI;

        inputPitchOffset.setText(String.format(Locale.getDefault(), "%.1f", pitchDeg));
        inputRollOffset.setText(String.format(Locale.getDefault(), "%.1f", rollDeg));
        
        Toast.makeText(this, "Calibrated from current position", Toast.LENGTH_SHORT).show();
    }
}
