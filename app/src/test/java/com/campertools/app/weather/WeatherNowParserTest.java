package com.campertools.app.weather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeatherNowParserTest {

    @Test
    public void parseCurrentAndNext24hExtractsRollingSummary() throws Exception {
        JSONObject root = new JSONObject();
        root.put("current_weather", new JSONObject()
                .put("temperature", 12.5)
                .put("windspeed", 18.2)
                .put("winddirection", 135.0));

        JSONArray temps = new JSONArray();
        JSONArray precip = new JSONArray();
        JSONArray weatherCode = new JSONArray();
        for (int i = 0; i < 30; i++) {
            temps.put(5 + i); // 5..34
            precip.put(i % 4 == 0 ? 0.6 : 0.0);
            weatherCode.put(i == 10 ? 95 : 61);
        }

        root.put("hourly", new JSONObject()
                .put("temperature_2m", temps)
                .put("precipitation", precip)
                .put("weathercode", weatherCode));

        Calendar clock = Calendar.getInstance(Locale.US);
        clock.set(Calendar.HOUR_OF_DAY, 2);

        WeatherNowSnapshot snapshot = WeatherNowParser.parseCurrentAndNext24h(root.toString(), clock);
        assertNotNull(snapshot);
        assertEquals(12.5, snapshot.currentTempC, 0.0001);
        assertEquals(18.2, snapshot.currentWindKmh, 0.0001);
        assertEquals(135.0, snapshot.currentWindDirectionDeg, 0.0001);
        assertEquals(7.0, snapshot.minTempC, 0.0001);
        assertEquals(30.0, snapshot.maxTempC, 0.0001);
        assertTrue(snapshot.anyPrecip);
        assertTrue(snapshot.anyThunderCode);
        assertEquals(3.6, snapshot.sumPrecipMm, 0.0001);
    }

    @Test
    public void parseReturnsNullWhenHourlyWindowIsInvalid() throws Exception {
        JSONObject root = new JSONObject();
        root.put("current_weather", new JSONObject()
                .put("temperature", 10.0)
                .put("windspeed", 5.0)
                .put("winddirection", 90.0));

        root.put("hourly", new JSONObject()
                .put("temperature_2m", new JSONArray().put(1).put(2))
                .put("precipitation", new JSONArray().put(0).put(0))
                .put("weathercode", new JSONArray().put(0).put(0)));

        Calendar clock = Calendar.getInstance(Locale.US);
        clock.set(Calendar.HOUR_OF_DAY, 23);

        WeatherNowSnapshot snapshot = WeatherNowParser.parseCurrentAndNext24h(root.toString(), clock);
        assertNull(snapshot);
    }
}
