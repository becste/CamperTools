package com.campertools.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;

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
    private static final int REQUEST_CAMERA_PERMISSION = 1003;
    private static final int REQUEST_SETTINGS = 2001;
    private static final String DONATION_PRODUCT_ID = "donationcoffee";
    private static final float ACCEL_ALPHA = 0.12f;
    private static final float MAG_ALPHA = 0.10f;
    private static final float TILT_ALPHA = 0.12f;
    private static final float AZIMUTH_ALPHA = 0.18f;
    private static final float DEFAULT_SUPPORT_SPAN_MM = 70f;
    private static final String STATE_BUMP_HEIGHT = "state_bump_height";
    private static final String STATE_BUMP_ROLL = "state_bump_roll";
    private static final String PREFS = "campertools_prefs";
    private static final String PREF_BUMP_HEIGHT = "pref_bump_height";
    private static final String PREF_BUMP_ROLL = "pref_bump_roll";
    private static final String PREF_USE_IMPERIAL = "pref_use_imperial";
    private static final String PREF_USE_NIGHT_MODE = "pref_use_night_mode";
    private static final long WEATHER_CACHE_WINDOW_MS = 60_000L;
    private static final long LOCATION_TIMEOUT_MS = 12_000L;

    // Elevation UI
    private TextView textElevation;
    private TextView textStatus;
    private TextView buttonRefresh;

    // Weather UI
    private TextView textWeatherNow;
    private TextView textWeatherRange;
    private TextView textWind;
    private TextView textPrecip;
    private TextView buttonWeather;
    private TextView buttonExtraData;

    // Level UI
    private LevelView levelView;
    private CompassView compassView;
    private TextView textTilt;
    private SwitchMaterial switchCompass;
    private TextView textSettingsLink;

    // Flashlight UI
    private SwitchMaterial switchFlashlight;
    private SeekBar seekBrightness;

    // Donation
    private TextView buttonDonate;
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

    // Camera
    private CameraManager cameraManager;
    private String cameraId;
    private boolean flashlightOn = false;
    private int maxBrightnessLevel = 1;

    // Track if location request is for weather or elevation
    private boolean pendingWeather = false;
    private boolean showCompass = false;

    // State for units
    private boolean useImperial = false;
    private boolean useNightMode = false;
    private float lastBumpHeightMm = 0f;
    private float compensationMagnitude = 0f;
    private boolean compensationAppliesToRoll = false;
    private long lastWeatherFetchMs = 0L;
    private String lastWeatherKey = null;

    // Last known values
    public static Location cachedLocation;
    private String lastJsonWeather;
    private FusedLocationProviderClient fusedLocationClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LocationCallback pendingLocationCallback;
    private Runnable pendingLocationTimeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState != null) {
            lastBumpHeightMm = savedInstanceState.getFloat(STATE_BUMP_HEIGHT, 0f);
            compensationAppliesToRoll = savedInstanceState.getBoolean(STATE_BUMP_ROLL, false);
            useImperial = prefs.getBoolean(PREF_USE_IMPERIAL, false);
            useNightMode = prefs.getBoolean(PREF_USE_NIGHT_MODE, false);
        } else {
            lastBumpHeightMm = prefs.getFloat(PREF_BUMP_HEIGHT, 0f);
            compensationAppliesToRoll = prefs.getBoolean(PREF_BUMP_ROLL, false);
            useImperial = prefs.getBoolean(PREF_USE_IMPERIAL, false);
            useNightMode = prefs.getBoolean(PREF_USE_NIGHT_MODE, false);
        }
        compensationMagnitude = computeNormalizedOffset(Math.abs(lastBumpHeightMm), DEFAULT_SUPPORT_SPAN_MM);

        // Elevation UI
        textElevation = (TextView) findViewById(R.id.textElevation);
        textStatus = (TextView) findViewById(R.id.textStatus);
        buttonRefresh = (TextView) findViewById(R.id.buttonRefresh);

        // Weather UI
        textWeatherNow = (TextView) findViewById(R.id.textWeatherNow);
        textWeatherRange = (TextView) findViewById(R.id.textWeatherRange);
        textWind = (TextView) findViewById(R.id.textWind);
        textPrecip = (TextView) findViewById(R.id.textPrecip);
        buttonWeather = (TextView) findViewById(R.id.buttonWeather);
        buttonExtraData = (TextView) findViewById(R.id.buttonExtraData);

        // Level UI
        levelView = (LevelView) findViewById(R.id.levelView);
        compassView = (CompassView) findViewById(R.id.compassView);
        textTilt = (TextView) findViewById(R.id.textTilt);
        switchCompass = (SwitchMaterial) findViewById(R.id.switchCompass);
        textSettingsLink = (TextView) findViewById(R.id.textSettingsLink);

        // Flashlight
        switchFlashlight = findViewById(R.id.switchFlashlight);
        seekBrightness = findViewById(R.id.seekBrightness);

        // Donate UI
        buttonDonate = (TextView) findViewById(R.id.buttonDonate);

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
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        // Flashlight setup
        initFlashlight();

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
                        updateSensorRegistration();
                    }
                });
            }
        }

        // Open settings for bump compensation
        if (textSettingsLink != null) {
            textSettingsLink.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSettings();
                }
            });
        }

        // Refresh GPS (Elevation) button
        if (buttonRefresh != null) {
            buttonRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    textStatus.setText(getString(R.string.status_getting_elevation));
                    requestSingleLocation(false);
                }
            });
        }

        // Weather button
        buttonWeather.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cachedLocation != null) {
                    fetchWeather(cachedLocation.getLatitude(), cachedLocation.getLongitude());
                } else {
                    pendingWeather = true;
                    checkPermissionAndProceed();
                }
            }
        });


        // Extra data button
        buttonExtraData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SunActivity.class);
                if (lastJsonWeather != null) {
                    intent.putExtra("weather_json", lastJsonWeather);
                }
                startActivity(intent);
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
        if (cachedLocation == null) {
            pendingWeather = false;
            checkPermissionAndProceed();
        }

        // Ensure initial mode visibility
        updateModeViews();
        applyNightMode();
    }

    private void initFlashlight() {
        boolean hasFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        if (!hasFlash) {
            switchFlashlight.setEnabled(false);
            seekBrightness.setVisibility(View.GONE);
            return;
        }

        try {
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            cameraId = cameraManager.getCameraIdList()[0];
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                maxBrightnessLevel = cameraManager.getCameraCharacteristics(cameraId)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
            switchFlashlight.setEnabled(false);
            return;
        }

        if (maxBrightnessLevel > 1) {
            seekBrightness.setMax(maxBrightnessLevel - 1);
            seekBrightness.setProgress(0);
        } else {
            seekBrightness.setVisibility(View.GONE);
        }

        switchFlashlight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    buttonView.setChecked(false); // Revert switch state until permission is granted
                } else {
                    turnOnFlashlight();
                }
            } else {
                turnOffFlashlight();
            }
        });

        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && flashlightOn) {
                    setFlashlightBrightness(progress + 1);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void turnOnFlashlight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxBrightnessLevel > 1) {
                int level = seekBrightness.getProgress() + 1;
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
                seekBrightness.setVisibility(View.VISIBLE);
            } else {
                cameraManager.setTorchMode(cameraId, true);
                seekBrightness.setVisibility(View.GONE);
            }
            flashlightOn = true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void turnOffFlashlight() {
        try {
            cameraManager.setTorchMode(cameraId, false);
            flashlightOn = false;
            seekBrightness.setVisibility(View.GONE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setFlashlightBrightness(int level) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxBrightnessLevel > 1) {
            try {
                // Level must be >= 1
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
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
        if (cachedLocation != null) {
            updateElevation(cachedLocation);
        }
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

    private void setupBillingClient() {
        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && purchases != null) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                // Handle user cancellation
            } else {
                Toast.makeText(MainActivity.this, R.string.donation_error, Toast.LENGTH_SHORT).show();
            }
        };

        billingClient = BillingClient.newBuilder(this)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
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
                (billingResult, productDetailsList) -> {

                    if (productDetailsList != null && !productDetailsList.isEmpty()) {
                        donationProductDetails = productDetailsList.get(0);
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
            com.android.billingclient.api.ConsumeParams consumeParams =
                    com.android.billingclient.api.ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();

            billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.donation_success, Toast.LENGTH_LONG).show()));
        }
    }

    private void checkPermissionAndProceed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (pendingWeather) {
                requestWeatherLocation();
            } else {
                requestElevationLocation();
            }
            return;
        }

        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (hasFine && hasCoarse) {
            if (pendingWeather) {
                requestWeatherLocation();
            } else {
                requestElevationLocation();
            }
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_HEIGHT_MM, Math.abs(lastBumpHeightMm));
        intent.putExtra(SettingsActivity.EXTRA_APPLIES_ROLL, compensationAppliesToRoll);
        intent.putExtra(SettingsActivity.EXTRA_USE_IMPERIAL, useImperial);
        intent.putExtra(SettingsActivity.EXTRA_USE_NIGHT_MODE, useNightMode);
        startActivityForResult(intent, REQUEST_SETTINGS);
    }

    @SuppressWarnings("MissingPermission")
    private void requestElevationLocation() {
        if (locationManager == null && fusedLocationClient == null) {
            textStatus.setText(getString(R.string.status_no_location_manager));
            return;
        }

        textStatus.setText(getString(R.string.status_getting_elevation));
        requestSingleLocation(false);
    }

    @SuppressWarnings("MissingPermission")
    private void requestWeatherLocation() {
        if (cachedLocation != null) {
            fetchWeather(cachedLocation.getLatitude(), cachedLocation.getLongitude());
            return;
        }
        if (locationManager == null && fusedLocationClient == null) {
            textWeatherNow.setText(getString(R.string.weather_no_location_manager));
            textPrecip.setText(getString(R.string.precip_label));
            return;
        }

        textWeatherNow.setText(getString(R.string.weather_getting_location));
        textWeatherRange.setText(getString(R.string.weather_next_loading));
        textPrecip.setText(getString(R.string.precip_loading));
        requestSingleLocation(true);
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
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switchFlashlight.setChecked(true);
                turnOnFlashlight();
            } else {
                Toast.makeText(this, "Camera permission is required for the flashlight", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean hasFine = false;
            boolean hasCoarse = false;
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
        String cacheKey = String.format(Locale.US, "%.3f,%.3f", lat, lon);
        long now = System.currentTimeMillis();
        if (lastJsonWeather != null
                && lastWeatherKey != null
                && lastWeatherKey.equals(cacheKey)
                && (now - lastWeatherFetchMs) < WEATHER_CACHE_WINDOW_MS) {
            String[] texts = parseWeatherJson(lastJsonWeather);
            if (texts != null) {
                textWeatherNow.setText(texts[0]);
                textWeatherRange.setText(texts[1]);
                if (textWind != null) {
                    textWind.setText(texts[2]);
                }
                textPrecip.setText(texts[3]);
                return;
            }
        }

        textWeatherNow.setText(getString(R.string.weather_loading));
        textWeatherRange.setText(getString(R.string.weather_next_loading));
        if (textWind != null) {
            textWind.setText(getString(R.string.wind_loading));
        }
        textPrecip.setText(getString(R.string.precip_loading));

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                // Open-Meteo: hourly temp + precipitation + weathercode + extra data for SunActivity
                String urlStr =
                        "https://api.open-meteo.com/v1/forecast"
                                + "?latitude=" + lat
                                + "&longitude=" + lon
                                + "&hourly=temperature_2m,precipitation,weathercode,winddirection_10m,cloudcover,sunshine_duration,is_day"
                                + "&daily=sunrise,sunset,windgusts_10m_max"
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
                lastWeatherFetchMs = System.currentTimeMillis();
                lastWeatherKey = cacheKey;

                final String[] resultTexts = parseWeatherJson(json);

                runOnUiThread(() -> {
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
                });

            } catch (final Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textWeatherNow.setText(getString(R.string.weather_error_with_message, e.getMessage()));
                    textWeatherRange.setText(getString(R.string.weather_range_label));
                    if (textWind != null) {
                        textWind.setText(getString(R.string.wind_label));
                    }
                    textPrecip.setText(getString(R.string.precip_label));
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String[] parseWeatherJson(String json) {
        try {
            JSONObject root = new JSONObject(json);

            JSONObject current = root.getJSONObject("current_weather");
            double currentTemp = current.getDouble("temperature");

            JSONObject hourly = root.getJSONObject("hourly");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray precip = hourly.getJSONArray("precipitation");
            JSONArray weathercode = hourly.getJSONArray("weathercode");
            JSONArray windDir = hourly.getJSONArray("winddirection_10m");

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
                double currentTempF = (currentTemp * 9 / 5) + 32;
                double minF = (min * 9 / 5) + 32;
                double maxF = (max * 9 / 5) + 32;

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

            return new String[]{nowText, rangeText, windText, precipText};

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isSnowCode(int code) {
        return code == 71 || code == 73 || code == 75 || code == 77
                || code == 85 || code == 86;
    }

    private boolean isThunderCode(int code) {
        return code == 95 || code == 96 || code == 99;
    }

    private boolean isFreezingCode(int code) {
        return code == 56 || code == 57 || code == 66 || code == 67;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        updateSensorRegistration();
    }

    private void updateSensorRegistration() {
        if (sensorManager == null || magnetometer == null) {
            return;
        }
        sensorManager.unregisterListener(this, magnetometer);
        if (showCompass) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        cancelPendingLocationRequests();
        if (flashlightOn) {
            turnOffFlashlight();
            switchFlashlight.setChecked(false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloat(STATE_BUMP_HEIGHT, lastBumpHeightMm);
        outState.putBoolean(STATE_BUMP_ROLL, compensationAppliesToRoll);
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed
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

            float offsetX = 0f;
            float offsetY = 0f;
            if (compensationMagnitude > 0f) {
                float sign = Math.signum(lastBumpHeightMm);
                if (compensationAppliesToRoll) {
                    offsetX = sign * compensationMagnitude;
                } else {
                    offsetY = sign * compensationMagnitude;
                }
            }

            float adjustedX = clampUnit(smoothNormX - offsetX);
            float adjustedY = clampUnit(smoothNormY - offsetY);

            if (levelView != null) {
                levelView.setTilt(adjustedX, adjustedY);
            }

            double pitchDeg = -Math.asin(adjustedY) * 180.0 / Math.PI;
            double rollDeg = Math.asin(adjustedX) * 180.0 / Math.PI;

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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK && data != null) {
            lastBumpHeightMm = data.getFloatExtra(SettingsActivity.EXTRA_HEIGHT_MM, 0f);
            compensationAppliesToRoll = data.getBooleanExtra(SettingsActivity.EXTRA_APPLIES_ROLL, false);
            compensationMagnitude = computeNormalizedOffset(Math.abs(lastBumpHeightMm), DEFAULT_SUPPORT_SPAN_MM);
            useImperial = data.getBooleanExtra(SettingsActivity.EXTRA_USE_IMPERIAL, false);
            useNightMode = data.getBooleanExtra(SettingsActivity.EXTRA_USE_NIGHT_MODE, false);

            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putFloat(PREF_BUMP_HEIGHT, lastBumpHeightMm)
                    .putBoolean(PREF_BUMP_ROLL, compensationAppliesToRoll)
                    .putBoolean(PREF_USE_IMPERIAL, useImperial)
                    .putBoolean(PREF_USE_NIGHT_MODE, useNightMode)
                    .apply();
            updateTiltAndCompass();
            refreshAllDisplays();
            applyNightMode();
        }
    }

    private void applyNightMode() {
        if (levelView != null) {
            levelView.setNightMode(useNightMode);
        }

        int highlightColor;
        if (useNightMode) {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = 0.01f; // Dim the screen significantly
            getWindow().setAttributes(layout);
            findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.background_color));
            highlightColor = ContextCompat.getColor(this, R.color.red_500);
        } else {
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; // System default
            getWindow().setAttributes(layout);
            findViewById(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, R.color.background_color));
            highlightColor = ContextCompat.getColor(this, R.color.teal_200);
        }

        if (buttonRefresh != null) buttonRefresh.setTextColor(highlightColor);
        if (buttonWeather != null) buttonWeather.setTextColor(highlightColor);
        if (buttonExtraData != null) buttonExtraData.setTextColor(highlightColor);
        if (buttonDonate != null) buttonDonate.setTextColor(highlightColor);
        if (textSettingsLink != null) textSettingsLink.setTextColor(highlightColor);
        if (weatherCredit != null) weatherCredit.setLinkTextColor(highlightColor);
    }

    private void applyLowPass(float[] input, float[] output, float alpha) {
        for (int i = 0; i < input.length; i++) {
            output[i] += alpha * (input[i] - output[i]);
        }
    }

    private void requestSingleLocation(final boolean forWeather) {
        cancelPendingLocationRequests();

        if (fusedLocationClient == null && locationManager != null) {
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!gpsEnabled) {
                if (forWeather) {
                    textWeatherNow.setText(getString(R.string.weather_gps_disabled));
                    textPrecip.setText(getString(R.string.precip_label));
                } else {
                    textStatus.setText(getString(R.string.status_gps_disabled));
                }
                return;
            }
        }

        if (fusedLocationClient != null) {
            LocationRequest request = LocationRequest.create();
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            request.setInterval(0);
            request.setFastestInterval(0);
            request.setNumUpdates(1);

            pendingLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    cancelPendingLocationRequests();
                    if (locationResult == null || locationResult.getLastLocation() == null) {
                        fallbackLocation(forWeather);
                        return;
                    }
                    Location location = locationResult.getLastLocation();
                    handleLocationResult(forWeather, location);
                }
            };

            try {
                fusedLocationClient.requestLocationUpdates(request, pendingLocationCallback, Looper.getMainLooper());
                pendingLocationTimeout = () -> {
                    cancelPendingLocationRequests();
                    fallbackLocation(forWeather);
                };
                mainHandler.postDelayed(pendingLocationTimeout, LOCATION_TIMEOUT_MS);
                return;
            } catch (SecurityException se) {
                // Will fall back below
            }
        }

        fallbackLocation(forWeather);
    }

    private void cancelPendingLocationRequests() {
        if (fusedLocationClient != null && pendingLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(pendingLocationCallback);
        }
        pendingLocationCallback = null;
        if (pendingLocationTimeout != null) {
            mainHandler.removeCallbacks(pendingLocationTimeout);
            pendingLocationTimeout = null;
        }
    }

    private void fallbackLocation(final boolean forWeather) {
        if (locationManager == null) {
            if (forWeather) {
                textWeatherNow.setText(getString(R.string.weather_no_location_manager));
                textPrecip.setText(getString(R.string.precip_label));
            } else {
                textStatus.setText(getString(R.string.status_no_location_manager));
            }
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            if (forWeather) {
                textWeatherNow.setText(getString(R.string.weather_gps_disabled));
                textPrecip.setText(getString(R.string.precip_label));
            } else {
                textStatus.setText(getString(R.string.status_gps_disabled));
            }
            return;
        }

        locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                location -> handleLocationResult(forWeather, location),
                Looper.getMainLooper()
        );
    }

    private void handleLocationResult(boolean forWeather, Location location) {
        if (location == null) {
            return;
        }
        cachedLocation = location;
        if (forWeather) {
            fetchWeather(location.getLatitude(), location.getLongitude());
        } else {
            updateElevation(location);
        }
    }

    private float computeNormalizedOffset(float heightMm, float spanMm) {
        float denom = (float) Math.sqrt(heightMm * heightMm + spanMm * spanMm);
        if (denom == 0f) {
            return 0f;
        }
        float normalized = heightMm / denom;
        return clampUnit(normalized);
    }

    private float clampUnit(float value) {
        if (value > 1f) return 1f;
        if (value < -1f) return -1f;
        return value;
    }

    private int directionBucketIndex(double deg) {
        // Normalize degrees to [0,360)
        double normalized = ((deg % 360.0) + 360.0) % 360.0;
        // 8 buckets of 45 degrees, centered on N, NE, E...
        int idx = (int) Math.round(normalized / 45.0) % 8;
        return idx;
    }
}
