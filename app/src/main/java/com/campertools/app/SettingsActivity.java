package com.campertools.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_HEIGHT_MM = "heightMm";
    public static final String EXTRA_APPLIES_ROLL = "appliesRoll";
    public static final String EXTRA_INVERT = "invertDirection";
    public static final String EXTRA_USE_IMPERIAL = "useImperial";

    private EditText inputCameraBump;
    private RadioGroup radioBumpAxis;
    private CheckBox checkInvert;
    private TextView buttonSave;
    private TextView textBack;
    private boolean useImperial = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        inputCameraBump = findViewById(R.id.inputCameraBump);
        radioBumpAxis = findViewById(R.id.radioBumpAxis);
        checkInvert = findViewById(R.id.checkInvertDirection);
        buttonSave = findViewById(R.id.buttonSave);
        textBack = findViewById(R.id.textBack);

        Intent intent = getIntent();
        float heightMm = intent.getFloatExtra(EXTRA_HEIGHT_MM, 0f);
        boolean appliesRoll = intent.getBooleanExtra(EXTRA_APPLIES_ROLL, false);
        boolean invert = intent.getBooleanExtra(EXTRA_INVERT, false);
        useImperial = intent.getBooleanExtra(EXTRA_USE_IMPERIAL, false);

        // Populate UI
        if (heightMm > 0f) {
            double displayValue = useImperial ? (heightMm / 25.4) : heightMm;
            inputCameraBump.setText(String.format(java.util.Locale.getDefault(), "%.1f", displayValue));
        }
        radioBumpAxis.check(appliesRoll ? R.id.radioBumpAxisRoll : R.id.radioBumpAxisPitch);
        checkInvert.setChecked(invert);

        if (textBack != null) {
            textBack.setOnClickListener(v -> finish());
        }

        buttonSave.setOnClickListener(v -> {
            String text = inputCameraBump.getText().toString().trim();
            double value;
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.camera_bump_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (value < 0.0) {
                Toast.makeText(this, R.string.camera_bump_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            double heightValueMm = useImperial ? value * 25.4 : value;
            boolean rollSelected = radioBumpAxis.getCheckedRadioButtonId() == R.id.radioBumpAxisRoll;
            boolean invertDir = checkInvert.isChecked();

            Intent result = new Intent();
            result.putExtra(EXTRA_HEIGHT_MM, (float) heightValueMm);
            result.putExtra(EXTRA_APPLIES_ROLL, rollSelected);
            result.putExtra(EXTRA_INVERT, invertDir);
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
