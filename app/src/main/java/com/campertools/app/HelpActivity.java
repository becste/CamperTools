package com.campertools.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HelpActivity extends AppCompatActivity {

    private boolean useNightMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_help);

        final int padding = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left + padding, insets.top + padding, insets.right + padding, insets.bottom + padding);
            return WindowInsetsCompat.CONSUMED;
        });

        SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        useNightMode = prefs.getBoolean(AppPrefs.PREF_USE_NIGHT_MODE, false);

        Button buttonClose = findViewById(R.id.buttonClose);
        buttonClose.setOnClickListener(v -> finish());

        applyNightMode();
    }

    private void applyNightMode() {
        int textColor;
        int backgroundColor;

        if (useNightMode) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.01f;
            getWindow().setAttributes(layout);
            
            textColor = ContextCompat.getColor(this, R.color.red_500);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
        } else {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(layout);

            textColor = ContextCompat.getColor(this, R.color.primary_text);
            backgroundColor = ContextCompat.getColor(this, R.color.background_color);
        }
        
        findViewById(android.R.id.content).setBackgroundColor(backgroundColor);

        TextView[] textViews = {
            findViewById(R.id.textTitle),
            findViewById(R.id.textHeaderMain), findViewById(R.id.textBodyMain),
            findViewById(R.id.textHeaderWeather), findViewById(R.id.textBodyWeather),
            findViewById(R.id.textHeaderHeight), findViewById(R.id.textBodyHeight),
            findViewById(R.id.textHeaderSettings), findViewById(R.id.textBodySettings),
            findViewById(R.id.textHelpNote)
        };

        for (TextView tv : textViews) {
            if (tv != null) tv.setTextColor(textColor);
        }
        
        Button btn = findViewById(R.id.buttonClose);
        if (btn != null) {
             btn.setTextColor(textColor);
        }
    }
}
