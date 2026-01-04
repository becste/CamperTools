package com.campertools.app;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity implements SensorEventListener {

    public static final String EXTRA_HEIGHT_MM = "heightMm";
    public static final String EXTRA_APPLIES_ROLL = "appliesRoll";
    public static final String EXTRA_USE_IMPERIAL = "useImperial";
    public static final String EXTRA_USE_NIGHT_MODE = "useNightMode";

    private static final float DEFAULT_SUPPORT_SPAN_MM = 70f;
    private static final float ALPHA = 0.1f; // Filter factor

    private EditText inputCameraBump;
    private RadioGroup radioBumpAxis;
    private Button buttonAutoCalibrate;
    private SwitchMaterial switchUnits;
    private SwitchMaterial switchNightMode;
    private Button buttonBack;
    private boolean useImperial = false;
    private boolean useNightMode = false;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final float[] gravity = new float[3];
    private boolean hasGravity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        inputCameraBump = findViewById(R.id.inputCameraBump);
        radioBumpAxis = findViewById(R.id.radioBumpAxis);
        buttonAutoCalibrate = findViewById(R.id.buttonAutoCalibrate);
        switchUnits = findViewById(R.id.switchUnits);
        switchNightMode = findViewById(R.id.switchNightMode);
        buttonBack = findViewById(R.id.buttonBack);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        Intent intent = getIntent();
        float heightMm = intent.getFloatExtra(EXTRA_HEIGHT_MM, 0f);
        boolean appliesRoll = intent.getBooleanExtra(EXTRA_APPLIES_ROLL, false);
        useImperial = intent.getBooleanExtra(EXTRA_USE_IMPERIAL, false);
        useNightMode = intent.getBooleanExtra(EXTRA_USE_NIGHT_MODE, false);

        // Populate UI
        double displayValue = useImperial ? (heightMm / 25.4) : heightMm;
        inputCameraBump.setText(String.format(Locale.getDefault(), "%.1f", displayValue));
        radioBumpAxis.check(appliesRoll ? R.id.radioBumpAxisRoll : R.id.radioBumpAxisPitch);
        switchUnits.setChecked(useImperial);

        switchUnits.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useImperial = isChecked;
        });

        if (switchNightMode != null) {
            switchNightMode.setChecked(useNightMode);
            switchNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                useNightMode = isChecked;
            });
        }

        if (buttonBack != null) {
            buttonBack.setOnClickListener(v -> {
                saveSettings();
                finish();
            });
        }

        if (buttonAutoCalibrate != null) {
            buttonAutoCalibrate.setOnClickListener(v -> performAutoCalibration());
        }
    }

    private void saveSettings() {
        String text = inputCameraBump.getText().toString().trim();
        double value = 0.0;
        if (!text.isEmpty()) {
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.camera_bump_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        double heightValueMm = useImperial ? value * 25.4 : value;
        boolean rollSelected = radioBumpAxis.getCheckedRadioButtonId() == R.id.radioBumpAxisRoll;

        Intent result = new Intent();
        result.putExtra(EXTRA_HEIGHT_MM, (float) heightValueMm);
        result.putExtra(EXTRA_APPLIES_ROLL, rollSelected);
        result.putExtra(EXTRA_USE_IMPERIAL, useImperial);
        result.putExtra(EXTRA_USE_NIGHT_MODE, useNightMode);
        setResult(RESULT_OK, result);
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
        float normX = gravity[0] / g;
        float normY = gravity[1] / g;

        boolean dominantIsY = Math.abs(normY) >= Math.abs(normX);
        float selectedNorm = dominantIsY ? normY : normX;

        float mag = Math.abs(selectedNorm);
        float sign = Math.signum(selectedNorm);

        if (mag >= 0.99f) {
            Toast.makeText(this, "Tilt too steep for calibration", Toast.LENGTH_SHORT).show();
            return;
        }

        double h = (mag * DEFAULT_SUPPORT_SPAN_MM) / Math.sqrt(1 - mag * mag);
        double signedH = h * sign;
        double displayValue = useImperial ? (signedH / 25.4) : signedH;

        inputCameraBump.setText(String.format(Locale.getDefault(), "%.1f", displayValue));
        radioBumpAxis.check(dominantIsY ? R.id.radioBumpAxisPitch : R.id.radioBumpAxisRoll);
        
        Toast.makeText(this, "Calibrated from current position", Toast.LENGTH_SHORT).show();
    }
}
