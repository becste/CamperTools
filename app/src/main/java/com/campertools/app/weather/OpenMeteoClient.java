package com.campertools.app.weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class OpenMeteoClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private OpenMeteoClient() {
    }

    public static String fetchForecastJson(double latitude, double longitude) throws IOException {
        String urlStr =
                "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + latitude
                        + "&longitude=" + longitude
                        + "&hourly=temperature_2m,precipitation,weathercode,winddirection_10m,cloudcover,sunshine_duration,is_day"
                        + "&daily=sunrise,sunset,windgusts_10m_max,temperature_2m_max,temperature_2m_min,precipitation_sum,winddirection_10m_dominant,weathercode"
                        + "&current_weather=true"
                        + "&timezone=auto";

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = readStream(connection.getErrorStream());
                throw new IOException("HTTP " + responseCode + (errorBody.isEmpty() ? "" : (": " + errorBody)));
            }

            return readStream(connection.getInputStream());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
