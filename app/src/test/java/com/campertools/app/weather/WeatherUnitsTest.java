package com.campertools.app.weather;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WeatherUnitsTest {

    @Test
    public void cardinalDirectionWrapsCorrectly() {
        assertEquals("N", WeatherUnits.cardinalDirection(0));
        assertEquals("N", WeatherUnits.cardinalDirection(359));
        assertEquals("NE", WeatherUnits.cardinalDirection(45));
        assertEquals("W", WeatherUnits.cardinalDirection(270));
        assertEquals("N", WeatherUnits.cardinalDirection(-1));
    }

    @Test
    public void unitConversionsAreStable() {
        assertEquals(32.0, WeatherUnits.celsiusToFahrenheit(0), 0.0001);
        assertEquals(68.0, WeatherUnits.celsiusToFahrenheit(20), 0.0001);
        assertEquals(6.21371, WeatherUnits.kmhToMph(10), 0.0001);
        assertEquals(0.393701, WeatherUnits.mmToInches(10), 0.0001);
    }

    @Test
    public void precipitationBucketsMatchThresholds() {
        assertEquals(WeatherUnits.PRECIP_INTENSITY_VERY_LIGHT, WeatherUnits.precipitationIntensityBucket(0.8));
        assertEquals(WeatherUnits.PRECIP_INTENSITY_LIGHT, WeatherUnits.precipitationIntensityBucket(3.0));
        assertEquals(WeatherUnits.PRECIP_INTENSITY_MODERATE, WeatherUnits.precipitationIntensityBucket(10.0));
        assertEquals(WeatherUnits.PRECIP_INTENSITY_HEAVY, WeatherUnits.precipitationIntensityBucket(20.0));
    }

    @Test
    public void precipitationTypeUsesPriorityOrder() {
        assertEquals(
                WeatherUnits.PRECIP_TYPE_THUNDERSTORM_RAIN,
                WeatherUnits.precipitationTypeBucket(10.0, false, true, false)
        );
        assertEquals(
                WeatherUnits.PRECIP_TYPE_SNOW,
                WeatherUnits.precipitationTypeBucket(0.5, true, false, false)
        );
        assertEquals(
                WeatherUnits.PRECIP_TYPE_RAIN_SNOW_MIX,
                WeatherUnits.precipitationTypeBucket(2.0, false, false, true)
        );
        assertEquals(
                WeatherUnits.PRECIP_TYPE_RAIN,
                WeatherUnits.precipitationTypeBucket(7.0, false, false, false)
        );
    }
}
