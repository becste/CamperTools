package com.campertools.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements SensorEventListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;

    // Elevation UI
    private TextView textElevation;
    private TextView textStatus;
    private Button buttonRefresh;

    // Weather UI
    private TextView textWeatherNow;
    private TextView textWeatherRange;
    private TextView textPrecip;
    private Button buttonWeather;

    // Level UI
    private LevelView levelView;
    private TextView textTilt;

    // Location
    private LocationManager locationManager;

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // Track if location request is for weather or elevation
    private boolean pendingWeather = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        // Elevation UI
        textElevation = (TextView) findViewById(R.id.textElevation);
        textStatus    = (TextView) findViewById(R.id.textStatus);
        buttonRefresh = (Button) findViewById(R.id.buttonRefresh);

        // Weather UI
        textWeatherNow   = (TextView) findViewById(R.id.textWeatherNow);
        textWeatherRange = (TextView) findViewById(R.id.textWeatherRange);
        textPrecip       = (TextView) findViewById(R.id.textPrecip);
        buttonWeather    = (Button) findViewById(R.id.buttonWeather);

        // Level UI
        levelView = (LevelView) findViewById(R.id.levelView);
        textTilt  = (TextView) findViewById(R.id.textTilt);

        // Location services
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Sensor services
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Elevation refresh button
        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pendingWeather = false;
                checkPermissionAndProceed();
            }
        });

        // Weather button
        buttonWeather.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pendingWeather = true;
                checkPermissionAndProceed();
            }
        });

        // Initial elevation request
        pendingWeather = false;
        checkPermissionAndProceed();
    }

    // ========= Permission / location handling =========

    private void checkPermissionAndProceed() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                if (pendingWeather) {
                    requestWeatherLocation();
                } else {
                    requestElevationLocation();
                }
            } else {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSION
                );
            }
        } else {
            if (pendingWeather) {
                requestWeatherLocation();
            } else {
                requestElevationLocation();
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestElevationLocation() {
        if (locationManager == null) {
            textStatus.setText("No LocationManager found");
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            textStatus.setText("GPS disabled in settings");
            return;
        }

        textStatus.setText("Getting location (elevation)...");

        locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        updateElevation(location);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {}
                },
                Looper.getMainLooper()
        );
    }

    @SuppressWarnings("MissingPermission")
    private void requestWeatherLocation() {
        if (locationManager == null) {
            textWeatherNow.setText("Weather: no LocationManager");
            textPrecip.setText("Precipitation (24h): –");
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            textWeatherNow.setText("Weather: GPS disabled");
            textPrecip.setText("Precipitation (24h): –");
            return;
        }

        textWeatherNow.setText("Weather: getting location...");
        textWeatherRange.setText("Next 24h: …");
        textPrecip.setText("Precipitation (24h): …");

        locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        fetchWeather(lat, lon);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {}
                },
                Looper.getMainLooper()
        );
    }

    private void updateElevation(Location location) {
        double altitude = location.getAltitude();  // ellipsoid height in meters

        // Snap to 0 if we are near sea level
        if (Math.abs(altitude) < 30.0) {
            altitude = 0.0;
        }

        String elevationString = String.format(
                Locale.getDefault(),
                "Elevation: %.0f m",
                altitude
        );

        textElevation.setText(elevationString);
        textStatus.setText("GPS OK");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults != null &&
                    grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                if (pendingWeather) {
                    requestWeatherLocation();
                } else {
                    requestElevationLocation();
                }

            } else {
                textStatus.setText("Permission denied");
                textWeatherNow.setText("Weather: permission denied");
                textPrecip.setText("Precipitation (24h): –");
            }
        }
    }

    // ========= Weather networking & parsing =========

    private void fetchWeather(final double lat, final double lon) {
        textWeatherNow.setText("Weather: loading...");
        textWeatherRange.setText("Next 24h: …");
        textPrecip.setText("Precipitation (24h): …");

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    // Open-Meteo: hourly temp + precipitation + weathercode
                    String urlStr =
                            "https://api.open-meteo.com/v1/forecast"
                            + "?latitude=" + lat
                            + "&longitude=" + lon
                            + "&hourly=temperature_2m,precipitation,weathercode"
                            + "&current_weather=true"
                            + "&timezone=auto";

                    URL url = new URL(urlStr);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
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

                    // Parse JSON
                    final String[] resultTexts = parseWeatherJson(json);

                    // Update UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (resultTexts != null) {
                                textWeatherNow.setText(resultTexts[0]);
                                textWeatherRange.setText(resultTexts[1]);
                                textPrecip.setText(resultTexts[2]);
                            } else {
                                textWeatherNow.setText("Weather: parse error");
                                textWeatherRange.setText("Next 24h: –");
                                textPrecip.setText("Precipitation (24h): –");
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textWeatherNow.setText("Weather error: " + e.getMessage());
                            textWeatherRange.setText("Next 24h: –");
                            textPrecip.setText("Precipitation (24h): –");
                        }
                    });
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    /**
     * Returns:
     * [0] "Weather: X.X °C"
     * [1] "Next 24h: min ... / max ..."
     * [2] "Precipitation (24h): <summary>"
     */
    private String[] parseWeatherJson(String json) {
        try {
            JSONObject root = new JSONObject(json);

            // Current temp
            JSONObject current = root.getJSONObject("current_weather");
            double currentTemp = current.getDouble("temperature");

            // Hourly temps + precipitation + weathercode
            JSONObject hourly = root.getJSONObject("hourly");
            JSONArray temps      = hourly.getJSONArray("temperature_2m");
            JSONArray precip     = hourly.getJSONArray("precipitation");
            JSONArray weathercode = hourly.getJSONArray("weathercode");

            int count = Math.min(24,
                    Math.min(temps.length(), Math.min(precip.length(), weathercode.length())));
            if (count == 0) {
                return null;
            }

            double min = temps.getDouble(0);
            double max = temps.getDouble(0);
            double sumTemp = temps.getDouble(0);
            double sumPrecip = precip.getDouble(0);

            boolean anyPrecip = precip.getDouble(0) > 0.05;
            boolean anySnowCode = isSnowCode(weathercode.getInt(0));
            boolean anyThunderCode = isThunderCode(weathercode.getInt(0));
            boolean anyFreezingCode = isFreezingCode(weathercode.getInt(0));

            for (int i = 1; i < count; i++) {
                double t = temps.getDouble(i);
                double p = precip.getDouble(i);
                int code = weathercode.getInt(i);

                if (t < min) min = t;
                if (t > max) max = t;
                sumTemp += t;
                sumPrecip += p;

                if (p > 0.05) {
                    anyPrecip = true;
                }
                if (isSnowCode(code)) {
                    anySnowCode = true;
                }
                if (isThunderCode(code)) {
                    anyThunderCode = true;
                }
                if (isFreezingCode(code)) {
                    anyFreezingCode = true;
                }
            }

            double avgTemp = sumTemp / count;

            String nowText = String.format(
                    Locale.getDefault(),
                    "Weather: %.1f °C",
                    currentTemp
            );

            String rangeText = String.format(
                    Locale.getDefault(),
                    "Next 24h: min %.1f °C / max %.1f °C",
                    min,
                    max
            );

            String precipText;
            if (!anyPrecip || sumPrecip < 0.05) {
                precipText = "Precipitation (24h): none expected";
            } else {
                // Intensity based on total precipitation
                String intensity;
                if (sumPrecip < 1.0) {
                    intensity = "very light";
                } else if (sumPrecip < 5.0) {
                    intensity = "light";
                } else if (sumPrecip < 15.0) {
                    intensity = "moderate";
                } else {
                    intensity = "heavy";
                }

                // Type classification
                String type;
                if (anyThunderCode) {
                    type = "rain with thunderstorms";
                } else if (anySnowCode || avgTemp <= 1.0) {
                    type = "snow / wintry precipitation";
                } else if (anyFreezingCode || (avgTemp > 1.0 && avgTemp < 3.0)) {
                    type = "rain/snow mix possible";
                } else {
                    type = "rain";
                }

                precipText = String.format(
                        Locale.getDefault(),
                        "Precipitation (24h): %s %s, ~%.1f mm expected",
                        intensity,
                        type,
                        sumPrecip
                );
            }

            return new String[]{ nowText, rangeText, precipText };

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isSnowCode(int code) {
        // Open-Meteo / WMO: 71,72,73,75,77,85,86 are snow-related
        return code == 71 || code == 73 || code == 75 || code == 77
                || code == 85 || code == 86;
    }

    private boolean isThunderCode(int code) {
        // Thunderstorms
        return code == 95 || code == 96 || code == 99;
    }

    private boolean isFreezingCode(int code) {
        // Freezing drizzle / rain
        return code == 56 || code == 57 || code == 66 || code == 67;
    }

    // ========= Level (accelerometer) =========

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_UI
            );
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
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float ax = event.values[0];
        float ay = event.values[1];
        float az = event.values[2];

        float g = SensorManager.GRAVITY_EARTH;
        float normX = ax / g;
        float normY = ay / g;

        if (normX > 1f) normX = 1f;
        if (normX < -1f) normX = -1f;
        if (normY > 1f) normY = 1f;
        if (normY < -1f) normY = -1f;

        if (levelView != null) {
            levelView.setTilt(normX, normY);
        }

        double pitchDeg = -Math.asin(normY) * 180.0 / Math.PI;
        double rollDeg  =  Math.asin(normX) * 180.0 / Math.PI;

        if (textTilt != null) {
            String tiltText = String.format(
                    Locale.getDefault(),
                    "Tilt: %.1f° / %.1f°",
                    pitchDeg,
                    rollDeg
            );
            textTilt.setText(tiltText);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
}