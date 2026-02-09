package com.campertools.app;

import android.location.Location;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SunActivity extends AppCompatActivity {

    private TextView textSunrise;
    private TextView textSunset;
    private TextView textWindGusts;
    private TextView textSunshine;
    private TextView textCloudCover;
    private TextView textSunStatus;
    private TextView textBack;

    // Forecast UI
    private TextView textForecastHeader;
    private View divider;
    private TextView[] dayDates = new TextView[3];
    private TextView[] dayTemps = new TextView[3];
    private TextView[] dayPrecips = new TextView[3];
    private TextView[] dayWinds = new TextView[3];

    private static String lastJsonSunData;
    private boolean useImperial = false;
    private boolean useNightMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sun);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContainer), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        android.content.SharedPreferences prefs = getSharedPreferences("campertools_prefs", MODE_PRIVATE);
        useImperial = prefs.getBoolean("pref_use_imperial", false);
        useNightMode = prefs.getBoolean("pref_use_night_mode", false);

        textSunrise = findViewById(R.id.textSunrise);
        textSunset = findViewById(R.id.textSunset);
        textWindGusts = findViewById(R.id.textWindGusts);
        textSunshine = findViewById(R.id.textSunshine);
        textCloudCover = findViewById(R.id.textCloudCover);
        textSunStatus = findViewById(R.id.textSunStatus);
        textBack = findViewById(R.id.textBack);

        textForecastHeader = findViewById(R.id.textForecastHeader);
        divider = findViewById(R.id.divider);

        dayDates[0] = findViewById(R.id.textDay0Date);
        dayTemps[0] = findViewById(R.id.textDay0Temp);
        dayPrecips[0] = findViewById(R.id.textDay0Precip);
        dayWinds[0] = findViewById(R.id.textDay0Wind);

        dayDates[1] = findViewById(R.id.textDay1Date);
        dayTemps[1] = findViewById(R.id.textDay1Temp);
        dayPrecips[1] = findViewById(R.id.textDay1Precip);
        dayWinds[1] = findViewById(R.id.textDay1Wind);

        dayDates[2] = findViewById(R.id.textDay2Date);
        dayTemps[2] = findViewById(R.id.textDay2Temp);
        dayPrecips[2] = findViewById(R.id.textDay2Precip);
        dayWinds[2] = findViewById(R.id.textDay2Wind);

        applyNightMode();

        textBack.setOnClickListener(v -> finish());

        if (getIntent().hasExtra("weather_json")) {
            String json = getIntent().getStringExtra("weather_json");
            parseAndDisplaySunData(json);
        } else if (lastJsonSunData != null) {
            parseAndDisplaySunData(lastJsonSunData);
        } else {
            Location cachedLocation = MainActivity.cachedLocation;
            if (cachedLocation != null) {
                fetchSunData(cachedLocation);
            } else {
                textSunStatus.setText("");
            }
        }
    }

    private void fetchSunData(Location location) {
        textSunStatus.setText(getString(R.string.fetching_weather));
        new Thread(() -> {
            try {
                // Fetch daily wind gusts, and hourly cloudcover, sunshine_duration, is_day
                // Updated URL with daily params
                String urlStr =
                        "https://api.open-meteo.com/v1/forecast"
                                + "?latitude=" + location.getLatitude()
                                + "&longitude=" + location.getLongitude()
                                + "&daily=sunrise,sunset,windgusts_10m_max,temperature_2m_max,temperature_2m_min,precipitation_sum,winddirection_10m_dominant,weathercode"
                                + "&hourly=cloudcover,sunshine_duration,is_day"
                                + "&timezone=auto";

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                InputStream is = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                final String json = sb.toString();

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    parseAndDisplaySunData(json);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    textSunStatus.setText(getString(R.string.error_fetching_weather));
                });
            }
        }).start();
    }

    private void parseAndDisplaySunData(String json) {
        try {
            lastJsonSunData = json;
            JSONObject root = new JSONObject(json);
            
            // Daily Data
            JSONObject daily = root.getJSONObject("daily");
            JSONArray sunriseArray = daily.getJSONArray("sunrise");
            JSONArray sunsetArray = daily.getJSONArray("sunset");
            JSONArray windGustsArray = daily.optJSONArray("windgusts_10m_max");
            
            // Forecast Data Arrays
            JSONArray tempMaxArray = daily.optJSONArray("temperature_2m_max");
            JSONArray tempMinArray = daily.optJSONArray("temperature_2m_min");
            JSONArray precipSumArray = daily.optJSONArray("precipitation_sum");
            JSONArray windDirArray = daily.optJSONArray("winddirection_10m_dominant");
            JSONArray weatherCodeArray = daily.optJSONArray("weathercode");
            JSONArray timeArray = daily.optJSONArray("time");

            // Hourly Data
            JSONObject hourly = root.getJSONObject("hourly");
            JSONArray cloudCoverArray = hourly.optJSONArray("cloudcover");
            JSONArray sunshineHourlyArray = hourly.optJSONArray("sunshine_duration");
            JSONArray isDayArray = hourly.optJSONArray("is_day");

            if (sunriseArray.length() > 0 && sunsetArray.length() > 0) {
                String sunriseISO = sunriseArray.getString(0);
                String sunsetISO = sunsetArray.getString(0);

                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault());
                Date sunriseDate = isoFormat.parse(sunriseISO);
                Date sunsetDate = isoFormat.parse(sunsetISO);

                String timePattern = useImperial ? "h:mm a" : "HH:mm";
                SimpleDateFormat timeFormat = new SimpleDateFormat(timePattern, Locale.getDefault());
                textSunrise.setText(String.format(getString(R.string.sunrise_label), timeFormat.format(sunriseDate)));
                textSunset.setText(String.format(getString(R.string.sunset_label), timeFormat.format(sunsetDate)));
                textSunStatus.setText("");
                
                // Wind Gusts
                if (windGustsArray != null && windGustsArray.length() > 0) {
                    double maxGustKmh = windGustsArray.getDouble(0);
                    if (useImperial) {
                        // km/h to mph
                        double maxGustMph = maxGustKmh * 0.621371;
                        textWindGusts.setText(String.format(Locale.getDefault(), getString(R.string.max_gusts_format_imperial), maxGustMph));
                    } else {
                        textWindGusts.setText(String.format(Locale.getDefault(), getString(R.string.max_gusts_format_metric), maxGustKmh));
                    }
                }

                // Calculate rolling 24h indices
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                int currentHourIndex = calendar.get(java.util.Calendar.HOUR_OF_DAY);
                
                // Sunshine Duration (Rolling 24h)
                if (sunshineHourlyArray != null && isDayArray != null) {
                    double totalSunshineSeconds = 0;
                    double totalDaylightSeconds = 0;
                    
                    int maxIndex = Math.min(sunshineHourlyArray.length(), isDayArray.length());
                    int loopCount = Math.min(24, maxIndex - currentHourIndex);

                    for (int i = 0; i < loopCount; i++) {
                        int idx = currentHourIndex + i;
                        totalSunshineSeconds += sunshineHourlyArray.getDouble(idx);
                        if (isDayArray.getInt(idx) == 1) {
                            totalDaylightSeconds += 3600.0;
                        }
                    }
                    
                    double sunshineHours = totalSunshineSeconds / 3600.0;
                    double percent = 0;
                    if (totalDaylightSeconds > 0) {
                        percent = (totalSunshineSeconds / totalDaylightSeconds) * 100.0;
                        if (percent > 100) percent = 100;
                    }

                    textSunshine.setText(String.format(Locale.getDefault(), getString(R.string.sunshine_rolling_format), sunshineHours, percent));
                }

                // Cloud Cover (Avg next 24h)
                if (cloudCoverArray != null && cloudCoverArray.length() > 0) {
                    double sumCloud = 0;
                    int maxIndex = cloudCoverArray.length();
                    int loopCount = Math.min(24, maxIndex - currentHourIndex);
                    
                    if (loopCount > 0) {
                        for (int i = 0; i < loopCount; i++) {
                            int idx = currentHourIndex + i;
                            sumCloud += cloudCoverArray.getDouble(idx);
                        }
                        double avgCloud = sumCloud / loopCount;
                        textCloudCover.setText(String.format(Locale.getDefault(), getString(R.string.cloud_cover_format), avgCloud));
                    }
                }
                
                // Forecast Population
                if (timeArray != null) {
                    SimpleDateFormat dayFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
                    SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    for (int i = 0; i < 3; i++) {
                        if (i >= timeArray.length()) break;
                        
                        // Date
                        String dateStr = timeArray.getString(i);
                        try {
                            Date d = inputDateFormat.parse(dateStr);
                            dayDates[i].setText(dayFormat.format(d));
                        } catch (Exception e) {
                            dayDates[i].setText(dateStr);
                        }

                        // Temp
                        if (tempMaxArray != null && tempMinArray != null) {
                            double min = tempMinArray.getDouble(i);
                            double max = tempMaxArray.getDouble(i);
                            if (useImperial) {
                                min = (min * 9/5) + 32;
                                max = (max * 9/5) + 32;
                                dayTemps[i].setText(String.format(Locale.getDefault(), "Low %.0f°F / High %.0f°F", min, max));
                            } else {
                                dayTemps[i].setText(String.format(Locale.getDefault(), "Low %.0f°C / High %.0f°C", min, max));
                            }
                        }

                        // Precip
                        if (precipSumArray != null) {
                            double p = precipSumArray.getDouble(i);
                            if (useImperial) {
                                double pIn = p * 0.0393701;
                                dayPrecips[i].setText(String.format(Locale.getDefault(), "Precip: %.2f in", pIn));
                            } else {
                                dayPrecips[i].setText(String.format(Locale.getDefault(), "Precip: %.1f mm", p));
                            }
                        }

                        // Wind
                        if (windDirArray != null) {
                            double w = windDirArray.getDouble(i);
                            String dir = getCardinalDirection(w);
                            dayWinds[i].setText("Wind: " + dir);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            textSunStatus.setText(getString(R.string.error_parsing_weather));
        }
    }

    private String getCardinalDirection(double deg) {
        String[] cardinal = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) Math.round(((deg % 360) / 45)) % 8;
        return cardinal[idx];
    }

    private void applyNightMode() {
        int backColor;
        int textColor;

        if (useNightMode) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.01f; // Dim the screen
            getWindow().setAttributes(layout);
            findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.background_color));
            backColor = ContextCompat.getColor(this, R.color.red_500);
            textColor = ContextCompat.getColor(this, R.color.red_500);
        } else {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; // System default
            getWindow().setAttributes(layout);
            findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.background_color));
            backColor = ContextCompat.getColor(this, R.color.teal_200);
            textColor = ContextCompat.getColor(this, R.color.primary_text);
        }

        if (textSunrise != null) textSunrise.setTextColor(textColor);
        if (textSunset != null) textSunset.setTextColor(textColor);
        if (textWindGusts != null) textWindGusts.setTextColor(textColor);
        if (textSunshine != null) textSunshine.setTextColor(textColor);
        if (textCloudCover != null) textCloudCover.setTextColor(textColor);
        if (textSunStatus != null) textSunStatus.setTextColor(textColor);
        if (textBack != null) textBack.setTextColor(backColor);
        
        if (textForecastHeader != null) textForecastHeader.setTextColor(textColor);
        // We can ignore divider color or tint it if needed, usually default gray is fine or hard to see in red mode.
        // Let's tint divider if night mode
        if (divider != null) {
             divider.setBackgroundColor(textColor);
             divider.setAlpha(0.5f);
        }

        for (int i=0; i<3; i++) {
            if (dayDates[i] != null) dayDates[i].setTextColor(textColor);
            if (dayTemps[i] != null) dayTemps[i].setTextColor(textColor);
            if (dayPrecips[i] != null) dayPrecips[i].setTextColor(textColor);
            if (dayWinds[i] != null) dayWinds[i].setTextColor(textColor);
        }
    }
}