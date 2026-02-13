package com.campertools.app.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public final class WeatherNowParser {

    private WeatherNowParser() {
    }

    public static WeatherNowSnapshot parseCurrentAndNext24h(String json, Calendar calendar) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject current = root.getJSONObject("current_weather");
            JSONObject hourly = root.getJSONObject("hourly");

            double currentTemp = current.getDouble("temperature");
            double currentWindSpeed = current.getDouble("windspeed");
            double currentWindDir = current.getDouble("winddirection");

            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray precip = hourly.getJSONArray("precipitation");
            JSONArray weatherCode = hourly.getJSONArray("weathercode");

            int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
            int maxLen = Math.min(temps.length(), Math.min(precip.length(), weatherCode.length()));
            int startIdx = currentHour;
            int endIdx = Math.min(maxLen, startIdx + 24);
            if (endIdx <= startIdx || startIdx >= maxLen) {
                return null;
            }

            double min = temps.getDouble(startIdx);
            double max = min;
            double sumTemp = 0d;
            double sumPrecip = 0d;
            boolean anyPrecip = false;
            boolean anySnow = false;
            boolean anyThunder = false;
            boolean anyFreezing = false;
            int count = 0;

            for (int i = startIdx; i < endIdx; i++) {
                double t = temps.getDouble(i);
                double p = precip.getDouble(i);
                int code = weatherCode.getInt(i);

                if (t < min) min = t;
                if (t > max) max = t;
                sumTemp += t;
                sumPrecip += p;
                anyPrecip |= p > 0.05;
                anySnow |= isSnowCode(code);
                anyThunder |= isThunderCode(code);
                anyFreezing |= isFreezingCode(code);
                count++;
            }

            if (count == 0) {
                return null;
            }

            double avgTemp = sumTemp / count;
            return new WeatherNowSnapshot(
                    currentTemp,
                    currentWindSpeed,
                    currentWindDir,
                    min,
                    max,
                    avgTemp,
                    sumPrecip,
                    anyPrecip,
                    anySnow,
                    anyThunder,
                    anyFreezing
            );
        } catch (JSONException e) {
            return null;
        }
    }

    private static boolean isSnowCode(int code) {
        return code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86;
    }

    private static boolean isThunderCode(int code) {
        return code == 95 || code == 96 || code == 99;
    }

    private static boolean isFreezingCode(int code) {
        return code == 56 || code == 57 || code == 66 || code == 67;
    }
}
