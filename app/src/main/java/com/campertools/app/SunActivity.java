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

import com.campertools.app.weather.OpenMeteoClient;
import com.campertools.app.weather.WeatherUnits;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SunActivity extends AppCompatActivity {

    private TextView textSunrise;
    private TextView textSunset;
    private TextView textWindGusts;
    private TextView textSunshine;
    private TextView textCloudCover;
    private TextView textSunStatus;
    private TextView textBack;
    private TextView textWeatherHeader;

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
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private Future<?> pendingSunTask;

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

        android.content.SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        useImperial = prefs.getBoolean(AppPrefs.PREF_USE_IMPERIAL, false);
        useNightMode = prefs.getBoolean(AppPrefs.PREF_USE_NIGHT_MODE, false);

        textWeatherHeader = findViewById(R.id.textWeatherHeader);
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

        if (getIntent().hasExtra(AppExtras.EXTRA_WEATHER_JSON)) {
            String json = getIntent().getStringExtra(AppExtras.EXTRA_WEATHER_JSON);
            parseAndDisplaySunData(json);
        } else if (lastJsonSunData != null) {
            parseAndDisplaySunData(lastJsonSunData);
        } else {
            Location cachedLocation = MainActivity.cachedLocation;
            if (cachedLocation != null) {
                fetchSunData(cachedLocation);
            } else {
                textSunStatus.setText(R.string.no_location_fix);
            }
        }
    }

    private void fetchSunData(Location location) {
        textSunStatus.setText(getString(R.string.fetching_weather));
        if (pendingSunTask != null) {
            pendingSunTask.cancel(true);
        }
        pendingSunTask = networkExecutor.submit(() -> {
            try {
                final String json = OpenMeteoClient.fetchForecastJson(
                        location.getLatitude(),
                        location.getLongitude()
                );
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    parseAndDisplaySunData(json);
                });

            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    textSunStatus.setText(getString(R.string.error_fetching_weather));
                });
            }
        });
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
                    String dir = "";
                    if (windDirArray != null && windDirArray.length() > 0) {
                        dir = getCardinalDirection(windDirArray.getDouble(0));
                    }
                    
                    if (useImperial) {
                        // km/h to mph
                        double maxGustMph = maxGustKmh * 0.621371;
                        textWindGusts.setText(String.format(Locale.getDefault(), getString(R.string.max_gusts_format_imperial), maxGustMph, dir));
                    } else {
                        textWindGusts.setText(String.format(Locale.getDefault(), getString(R.string.max_gusts_format_metric), maxGustKmh, dir));
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
                        int dataIdx = i + 1; // Start from index 1 (Tomorrow)
                        if (dataIdx >= timeArray.length()) break;
                        
                        // Date
                        String dateStr = timeArray.getString(dataIdx);
                        try {
                            Date d = inputDateFormat.parse(dateStr);
                            dayDates[i].setText(dayFormat.format(d));
                        } catch (Exception e) {
                            dayDates[i].setText(dateStr);
                        }

                        // Temp
                        if (tempMaxArray != null && tempMinArray != null && dataIdx < tempMaxArray.length()) {
                            double min = tempMinArray.getDouble(dataIdx);
                            double max = tempMaxArray.getDouble(dataIdx);
                            if (useImperial) {
                                min = WeatherUnits.celsiusToFahrenheit(min);
                                max = WeatherUnits.celsiusToFahrenheit(max);
                                dayTemps[i].setText(String.format(
                                        Locale.getDefault(),
                                        getString(R.string.forecast_temp_imperial),
                                        min,
                                        max
                                ));
                            } else {
                                dayTemps[i].setText(String.format(
                                        Locale.getDefault(),
                                        getString(R.string.forecast_temp_metric),
                                        min,
                                        max
                                ));
                            }
                        }

                        // Precip
                        if (precipSumArray != null && dataIdx < precipSumArray.length()) {
                            double p = precipSumArray.getDouble(dataIdx);
                            if (useImperial) {
                                dayPrecips[i].setText(String.format(
                                        Locale.getDefault(),
                                        getString(R.string.forecast_precip_imperial),
                                        WeatherUnits.mmToInches(p)
                                ));
                            } else {
                                dayPrecips[i].setText(String.format(
                                        Locale.getDefault(),
                                        getString(R.string.forecast_precip_metric),
                                        p
                                ));
                            }
                        }

                        // Wind
                        if (windDirArray != null && dataIdx < windDirArray.length()) {
                            double w = windDirArray.getDouble(dataIdx);
                            String dir = getCardinalDirection(w);
                            
                            if (windGustsArray != null && dataIdx < windGustsArray.length()) {
                                double maxGustKmh = windGustsArray.getDouble(dataIdx);
                                if (useImperial) {
                                    dayWinds[i].setText(String.format(
                                            Locale.getDefault(),
                                            getString(R.string.forecast_wind_imperial),
                                            WeatherUnits.kmhToMph(maxGustKmh),
                                            dir
                                    ));
                                } else {
                                    dayWinds[i].setText(String.format(
                                            Locale.getDefault(),
                                            getString(R.string.forecast_wind_metric),
                                            maxGustKmh,
                                            dir
                                    ));
                                }
                            } else {
                                dayWinds[i].setText(String.format(
                                        Locale.getDefault(),
                                        getString(R.string.forecast_wind_direction_only),
                                        dir
                                ));
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            textSunStatus.setText(getString(R.string.error_parsing_weather));
        }
    }

    private String getCardinalDirection(double deg) {
        return WeatherUnits.cardinalDirection(deg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingSunTask != null) {
            pendingSunTask.cancel(true);
            pendingSunTask = null;
        }
        networkExecutor.shutdownNow();
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

        if (textWeatherHeader != null) textWeatherHeader.setTextColor(textColor);
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
