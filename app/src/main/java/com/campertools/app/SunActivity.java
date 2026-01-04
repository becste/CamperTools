package com.campertools.app;

import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

    private static String lastJsonSunData;
    private boolean useImperial = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sun);

        android.content.SharedPreferences prefs = getSharedPreferences("campertools_prefs", MODE_PRIVATE);
        useImperial = prefs.getBoolean("pref_use_imperial", false);

        textSunrise = findViewById(R.id.textSunrise);
        textSunset = findViewById(R.id.textSunset);
        textWindGusts = findViewById(R.id.textWindGusts);
        textSunshine = findViewById(R.id.textSunshine);
        textCloudCover = findViewById(R.id.textCloudCover);
        textSunStatus = findViewById(R.id.textSunStatus);
        textBack = findViewById(R.id.textBack);

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
                textSunStatus.setText("No location fix. Please refresh GPS on the main screen.");
            }
        }
    }

    private void fetchSunData(Location location) {
        textSunStatus.setText("Fetching detailed weather data...");
        new Thread(() -> {
            try {
                // Fetch daily wind gusts, and hourly cloudcover, sunshine_duration, is_day
                String urlStr =
                        "https://api.open-meteo.com/v1/forecast"
                                + "?latitude=" + location.getLatitude()
                                + "&longitude=" + location.getLongitude()
                                + "&daily=sunrise,sunset,windgusts_10m_max"
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

                runOnUiThread(() -> parseAndDisplaySunData(json));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> textSunStatus.setText("Error fetching weather data."));
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

                SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                textSunrise.setText("Sunrise: " + timeFormat.format(sunriseDate));
                textSunset.setText("Sunset: " + timeFormat.format(sunsetDate));
                textSunStatus.setText("");
                
                // Wind Gusts
                if (windGustsArray != null && windGustsArray.length() > 0) {
                    double maxGustKmh = windGustsArray.getDouble(0);
                    if (useImperial) {
                        // km/h to mph
                        double maxGustMph = maxGustKmh * 0.621371;
                        textWindGusts.setText(String.format(Locale.getDefault(), "Max Gusts (24h): %.1f mph", maxGustMph));
                    } else {
                        textWindGusts.setText(String.format(Locale.getDefault(), "Max Gusts (24h): %.1f km/h", maxGustKmh));
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

                    textSunshine.setText(String.format(Locale.getDefault(), "Sunshine (next 24h): %.1f hrs (%.0f%%)", sunshineHours, percent));
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
                        textCloudCover.setText(String.format(Locale.getDefault(), "Cloud Cover (24h avg): %.0f%%", avgCloud));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            textSunStatus.setText("Error parsing detailed data.");
        }
    }
}
