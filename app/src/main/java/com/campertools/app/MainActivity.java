package com.campertools.app;

import android.Manifest;
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
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private static final String DONATION_PRODUCT_ID = "donationcoffee"; // You must create this product ID in Play Console
    private static final float ACCEL_ALPHA = 0.12f;   // Low-pass filter for accelerometer
    private static final float MAG_ALPHA = 0.10f;     // Low-pass filter for magnetometer
    private static final float TILT_ALPHA = 0.12f;    // Smoothing for tilt display
    private static final float AZIMUTH_ALPHA = 0.18f; // Smoothing for compass heading

    // Elevation UI
    private TextView textElevation;
    private TextView textStatus;
    private Button buttonRefresh;
    private MaterialSwitch switchUnits;

    // Weather UI
    private TextView textWeatherNow;
    private TextView textWeatherRange;
    private TextView textWind;
    private TextView textPrecip;
    private Button buttonWeather;

    // Level UI
    private LevelView levelView;
    private CompassView compassView;
    private TextView textTilt;
    private MaterialSwitch switchCompass;
    
    // Donation
    private Button buttonDonate;
    private BillingClient billingClient;
    private ProductDetails donationProductDetails;
    
    // Credit
    private TextView weatherCredit;

    // Location
    private LocationManager locationManager;

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] filteredAccel = new float[3];
    private final float[] filteredMag = new float[3];
    private final float[] accelReading = new float[3];
    private final float[] magnetReading = new float[3];
    private boolean accelInitialized = false;
    private boolean magInitialized = false;
    private boolean hasAccel = false;
    private boolean hasMag = false;
    private boolean tiltInitialized = false;
    private float smoothNormX = 0f;
    private float smoothNormY = 0f;
    private float smoothedAzimuthDeg = Float.NaN;

    // Track if location request is for weather or elevation
    private boolean pendingWeather = false;
    private boolean showCompass = false;
    
    // State for units
    private boolean useImperial = false;
    
    // Last known values
    private Location lastLocation;
    private String lastJsonWeather;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        // Elevation UI
        textElevation = (TextView) findViewById(R.id.textElevation);
        textStatus    = (TextView) findViewById(R.id.textStatus);
        buttonRefresh = (Button) findViewById(R.id.buttonRefresh);
        switchUnits   = (MaterialSwitch) findViewById(R.id.switchUnits);

        // Weather UI
        textWeatherNow   = (TextView) findViewById(R.id.textWeatherNow);
        textWeatherRange = (TextView) findViewById(R.id.textWeatherRange);
        textWind         = (TextView) findViewById(R.id.textWind);
        textPrecip       = (TextView) findViewById(R.id.textPrecip);
        buttonWeather    = (Button) findViewById(R.id.buttonWeather);

        // Level UI
        levelView = (LevelView) findViewById(R.id.levelView);
        compassView = (CompassView) findViewById(R.id.compassView);
        textTilt  = (TextView) findViewById(R.id.textTilt);
        switchCompass = (MaterialSwitch) findViewById(R.id.switchCompass);
        
        // Donate UI
        buttonDonate = (Button) findViewById(R.id.buttonDonate);
        
        // Weather Credit
        weatherCredit = (TextView) findViewById(R.id.weatherCredit);
        if (weatherCredit != null) {
            weatherCredit.setMovementMethod(LinkMovementMethod.getInstance());
        }

        // Location services
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Sensor services
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        // Elevation refresh button
        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pendingWeather = false;
                checkPermissionAndProceed();
            }
        });
        
        // Compass toggle
        if (switchCompass != null) {
            if (magnetometer == null) {
                switchCompass.setEnabled(false);
                Toast.makeText(this, "Compass sensor unavailable on this device", Toast.LENGTH_LONG).show();
            } else {
                switchCompass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        showCompass = isChecked;
                        updateModeViews();
                    }
                });
            }
        }

        // Units toggle
        switchUnits.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                useImperial = isChecked;
                refreshAllDisplays();
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
        
        // Donation button
        buttonDonate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchPurchaseFlow();
            }
        });
        
        // Initialize Billing
        setupBillingClient();

        // Initial elevation request
        pendingWeather = false;
        checkPermissionAndProceed();
        
        // Ensure initial mode visibility
        updateModeViews();
    }
    
    private void updateModeViews() {
        if (levelView != null) {
            levelView.setVisibility(showCompass ? View.GONE : View.VISIBLE);
        }
        if (compassView != null) {
            compassView.setVisibility(showCompass ? View.VISIBLE : View.GONE);
        }
        if (textTilt != null) {
            if (showCompass) {
                textTilt.setText(getString(R.string.heading_placeholder));
            } else {
                textTilt.setText(getString(R.string.tilt_label));
            }
        }
    }
    
    private void refreshAllDisplays() {
        // Refresh elevation text if we have a location
        if (lastLocation != null) {
            updateElevation(lastLocation);
        }
        // Refresh weather text if we have JSON data
        if (lastJsonWeather != null) {
            String[] texts = parseWeatherJson(lastJsonWeather);
            if (texts != null) {
                textWeatherNow.setText(texts[0]);
                textWeatherRange.setText(texts[1]);
                if (textWind != null) {
                    textWind.setText(texts[2]);
                }
                textPrecip.setText(texts[3]);
            }
        }
    }
    
    // ========= Billing / Donation =========
    
    private void setupBillingClient() {
        PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                        && purchases != null) {
                    for (Purchase purchase : purchases) {
                        handlePurchase(purchase);
                    }
                } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    // Handle user cancellation
                } else {
                    // Handle other errors
                    Toast.makeText(MainActivity.this, R.string.donation_error, Toast.LENGTH_SHORT).show();
                }
            }
        };

        billingClient = BillingClient.newBuilder(this)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build()
                )
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to Google Play
            }
        });
    }

    private void queryProductDetails() {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(DONATION_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        );

        QueryProductDetailsParams queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(
            queryProductDetailsParams,
            new ProductDetailsResponseListener() {
                public void onProductDetailsResponse(BillingResult billingResult,
                        QueryProductDetailsResult productDetailsResult) {
                    
                    List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && productDetailsList != null
                            && !productDetailsList.isEmpty()) {
                        donationProductDetails = productDetailsList.get(0);
                        // You could update the button text here with the price
                        // e.g. buttonDonate.setText("Donate " + donationProductDetails.getOneTimePurchaseOfferDetails().getFormattedPrice());
                    }
                }
            }
        );
    }
    
    private void launchPurchaseFlow() {
        if (donationProductDetails != null) {
            List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
            productDetailsParamsList.add(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(donationProductDetails)
                    .build()
            );

            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

            billingClient.launchBillingFlow(this, billingFlowParams);
        } else {
            Toast.makeText(this, "Billing service not ready or product not found", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Consume the purchase so they can donate again
            com.android.billingclient.api.ConsumeParams consumeParams =
                    com.android.billingclient.api.ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();

            billingClient.consumeAsync(consumeParams, new com.android.billingclient.api.ConsumeResponseListener() {
                @Override
                public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
                     runOnUiThread(new Runnable() {
                         @Override
                         public void run() {
                             Toast.makeText(MainActivity.this, R.string.donation_success, Toast.LENGTH_LONG).show();
                         }
                     });
                }
            });
        }
    }

    // ========= Permission / location handling =========

    private void checkPermissionAndProceed() {
        boolean hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (hasFine && hasCoarse) {
            if (pendingWeather) {
                requestWeatherLocation();
            } else {
                requestElevationLocation();
            }
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestElevationLocation() {
        if (locationManager == null) {
            textStatus.setText(getString(R.string.status_no_location_manager));
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            textStatus.setText(getString(R.string.status_gps_disabled));
            return;
        }

        textStatus.setText(getString(R.string.status_getting_elevation));

        locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        lastLocation = location;
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
            textWeatherNow.setText(getString(R.string.weather_no_location_manager));
            textPrecip.setText(getString(R.string.precip_label));
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            textWeatherNow.setText(getString(R.string.weather_gps_disabled));
            textPrecip.setText(getString(R.string.precip_label));
            return;
        }

        textWeatherNow.setText(getString(R.string.weather_getting_location));
        textWeatherRange.setText(getString(R.string.weather_next_loading));
        textPrecip.setText(getString(R.string.precip_loading));

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
        
        String elevationString;
        if (useImperial) {
            // Meters to feet
            double feet = altitude * 3.28084;
            elevationString = String.format(
                    Locale.getDefault(),
                    "Elevation: %.0f ft",
                    feet
            );
        } else {
            elevationString = String.format(
                    Locale.getDefault(),
                    "Elevation: %.0f m",
                    altitude
            );
        }

        textElevation.setText(elevationString);
        textStatus.setText(getString(R.string.status_gps_ok));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean hasFine = false;
            boolean hasCoarse = false;
            if (grantResults != null) {
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        String perm = permissions[i];
                        if (Manifest.permission.ACCESS_FINE_LOCATION.equals(perm)) {
                            hasFine = true;
                        } else if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(perm)) {
                            hasCoarse = true;
                        }
                    }
                }
            }

            if (hasFine && hasCoarse) {

                if (pendingWeather) {
                    requestWeatherLocation();
                } else {
                    requestElevationLocation();
                }

            } else {
                textStatus.setText(getString(R.string.permission_denied));
                textWeatherNow.setText(getString(R.string.weather_permission_denied));
                if (textWind != null) {
                    textWind.setText(getString(R.string.wind_label));
                }
                textPrecip.setText(getString(R.string.precip_label));
            }
        }
    }

    // ========= Weather networking & parsing =========

    private void fetchWeather(final double lat, final double lon) {
        textWeatherNow.setText(getString(R.string.weather_loading));
        textWeatherRange.setText(getString(R.string.weather_next_loading));
        if (textWind != null) {
            textWind.setText(getString(R.string.wind_loading));
        }
        textPrecip.setText(getString(R.string.precip_loading));

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
                            + "&hourly=temperature_2m,precipitation,weathercode,winddirection_10m"
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
                    lastJsonWeather = json;

                    // Parse JSON
                    final String[] resultTexts = parseWeatherJson(json);

                    // Update UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (resultTexts != null) {
                                textWeatherNow.setText(resultTexts[0]);
                                textWeatherRange.setText(resultTexts[1]);
                                if (textWind != null) {
                                    textWind.setText(resultTexts[2]);
                                }
                                textPrecip.setText(resultTexts[3]);
                            } else {
                                textWeatherNow.setText(getString(R.string.weather_parse_error));
                                textWeatherRange.setText(getString(R.string.weather_range_label));
                                if (textWind != null) {
                                    textWind.setText(getString(R.string.wind_label));
                                }
                                textPrecip.setText(getString(R.string.precip_label));
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textWeatherNow.setText(getString(R.string.weather_error_with_message, e.getMessage()));
                            textWeatherRange.setText(getString(R.string.weather_range_label));
                            if (textWind != null) {
                                textWind.setText(getString(R.string.wind_label));
                            }
                            textPrecip.setText(getString(R.string.precip_label));
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
     * [2] "Wind (24h): <summary>"
     * [3] "Precipitation (24h): <summary>"
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
            JSONArray windDir     = hourly.getJSONArray("winddirection_10m");

            int count = Math.min(24,
                    Math.min(temps.length(),
                            Math.min(precip.length(),
                                    Math.min(weathercode.length(), windDir.length()))));
            if (count == 0) {
                return null;
            }

            double min = temps.getDouble(0);
            double max = temps.getDouble(0);
            double sumTemp = temps.getDouble(0);
            double sumPrecip = precip.getDouble(0);
            double firstWindDeg = windDir.getDouble(0);

            boolean anyPrecip = precip.getDouble(0) > 0.05;
            boolean anySnowCode = isSnowCode(weathercode.getInt(0));
            boolean anyThunderCode = isThunderCode(weathercode.getInt(0));
            boolean anyFreezingCode = isFreezingCode(weathercode.getInt(0));
            int[] windBuckets = new int[8]; // N, NE, E, SE, S, SW, W, NW
            windBuckets[directionBucketIndex(firstWindDeg)]++;

            for (int i = 1; i < count; i++) {
                double t = temps.getDouble(i);
                double p = precip.getDouble(i);
                int code = weathercode.getInt(i);
                double w = windDir.getDouble(i);

                if (t < min) min = t;
                if (t > max) max = t;
                sumTemp += t;
                sumPrecip += p;
                windBuckets[directionBucketIndex(w)]++;

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

            String nowText;
            String rangeText;
            
            if (useImperial) {
                // Convert Celsius to Fahrenheit
                double currentTempF = (currentTemp * 9/5) + 32;
                double minF = (min * 9/5) + 32;
                double maxF = (max * 9/5) + 32;
                
                nowText = String.format(
                        Locale.getDefault(),
                        "Weather: %.1f °F",
                        currentTempF
                );
    
                rangeText = String.format(
                        Locale.getDefault(),
                        "Next 24h: min %.1f °F / max %.1f °F",
                        minF,
                        maxF
                );
            } else {
                nowText = String.format(
                        Locale.getDefault(),
                        "Weather: %.1f °C",
                        currentTemp
                );
    
                rangeText = String.format(
                        Locale.getDefault(),
                        "Next 24h: min %.1f °C / max %.1f °C",
                        min,
                        max
                );
            }

            String windText;
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

                if (useImperial) {
                    // mm to inches
                    double inches = sumPrecip * 0.0393701;
                    precipText = String.format(
                            Locale.getDefault(),
                            "Precipitation (24h): %s %s, ~%.2f in expected",
                            intensity,
                            type,
                            inches
                    );
                } else {
                    precipText = String.format(
                            Locale.getDefault(),
                            "Precipitation (24h): %s %s, ~%.1f mm expected",
                            intensity,
                            type,
                            sumPrecip
                    );
                }
            }

            int prevailingIndex = 0;
            for (int i = 1; i < windBuckets.length; i++) {
                if (windBuckets[i] > windBuckets[prevailingIndex]) {
                    prevailingIndex = i;
                }
            }
            String[] cardinal = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            windText = String.format(
                    Locale.getDefault(),
                    "Wind (24h): %s (most common direction)",
                    cardinal[prevailingIndex]
            );

            return new String[]{ nowText, rangeText, windText, precipText };

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
        if (sensorManager != null && magnetometer != null) {
            sensorManager.registerListener(
                    this,
                    magnetometer,
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
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            if (!accelInitialized) {
                System.arraycopy(event.values, 0, filteredAccel, 0, filteredAccel.length);
                accelInitialized = true;
            } else {
                applyLowPass(event.values, filteredAccel, ACCEL_ALPHA);
            }
            System.arraycopy(filteredAccel, 0, accelReading, 0, accelReading.length);
            hasAccel = true;
            updateTiltAndCompass();
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            if (!magInitialized) {
                System.arraycopy(event.values, 0, filteredMag, 0, filteredMag.length);
                magInitialized = true;
            } else {
                applyLowPass(event.values, filteredMag, MAG_ALPHA);
            }
            System.arraycopy(filteredMag, 0, magnetReading, 0, magnetReading.length);
            hasMag = true;
            updateTiltAndCompass();
        }
    }
    
    private void updateTiltAndCompass() {
        if (!showCompass && hasAccel) {
            float ax = accelReading[0];
            float ay = accelReading[1];
            float g = SensorManager.GRAVITY_EARTH;
            float normX = ax / g;
            float normY = ay / g;

            if (normX > 1f) normX = 1f;
            if (normX < -1f) normX = -1f;
            if (normY > 1f) normY = 1f;
            if (normY < -1f) normY = -1f;

            if (!tiltInitialized) {
                smoothNormX = normX;
                smoothNormY = normY;
                tiltInitialized = true;
            } else {
                smoothNormX += TILT_ALPHA * (normX - smoothNormX);
                smoothNormY += TILT_ALPHA * (normY - smoothNormY);
            }

            if (levelView != null) {
                levelView.setTilt(smoothNormX, smoothNormY);
            }

            double pitchDeg = -Math.asin(smoothNormY) * 180.0 / Math.PI;
            double rollDeg  =  Math.asin(smoothNormX) * 180.0 / Math.PI;

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
        
        if (showCompass && hasAccel && hasMag && compassView != null) {
            float[] rotationMatrix = new float[9];
            float[] inclinationMatrix = new float[9];
            boolean success = SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelReading, magnetReading);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientation);
                float azimuthRad = orientation[0];
                float azimuthDeg = (float) Math.toDegrees(azimuthRad);
                if (azimuthDeg < 0) {
                    azimuthDeg += 360;
                }
                if (Float.isNaN(smoothedAzimuthDeg)) {
                    smoothedAzimuthDeg = azimuthDeg;
                } else {
                    float delta = azimuthDeg - smoothedAzimuthDeg;
                    delta = (delta + 540f) % 360f - 180f; // shortest path around circle
                    smoothedAzimuthDeg += AZIMUTH_ALPHA * delta;
                    if (smoothedAzimuthDeg < 0) {
                        smoothedAzimuthDeg += 360f;
                    } else if (smoothedAzimuthDeg >= 360f) {
                        smoothedAzimuthDeg -= 360f;
                    }
                }
                float displayHeading = smoothedAzimuthDeg;
                compassView.setDirection(displayHeading);
                if (textTilt != null) {
                    String headingText = String.format(
                            Locale.getDefault(),
                            "Heading: %.0f°",
                            displayHeading
                    );
                    textTilt.setText(headingText);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
    
    private void applyLowPass(float[] input, float[] output, float alpha) {
        for (int i = 0; i < input.length; i++) {
            output[i] += alpha * (input[i] - output[i]);
        }
    }
    
    private int directionBucketIndex(double deg) {
        // Normalize degrees to [0,360)
        double normalized = ((deg % 360.0) + 360.0) % 360.0;
        // 8 buckets of 45 degrees, centered on N, NE, E...
        int idx = (int) Math.round(normalized / 45.0) % 8;
        return idx;
    }
}
