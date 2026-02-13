package com.campertools.app.weather;

public final class WeatherUnits {

    public static final int PRECIP_INTENSITY_VERY_LIGHT = 0;
    public static final int PRECIP_INTENSITY_LIGHT = 1;
    public static final int PRECIP_INTENSITY_MODERATE = 2;
    public static final int PRECIP_INTENSITY_HEAVY = 3;

    public static final int PRECIP_TYPE_RAIN = 0;
    public static final int PRECIP_TYPE_SNOW = 1;
    public static final int PRECIP_TYPE_RAIN_SNOW_MIX = 2;
    public static final int PRECIP_TYPE_THUNDERSTORM_RAIN = 3;

    private static final String[] CARDINAL = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private WeatherUnits() {
    }

    public static double celsiusToFahrenheit(double celsius) {
        return (celsius * 9d / 5d) + 32d;
    }

    public static double kmhToMph(double kmh) {
        return kmh * 0.621371d;
    }

    public static double mmToInches(double mm) {
        return mm * 0.0393701d;
    }

    public static String cardinalDirection(double degrees) {
        return CARDINAL[directionBucketIndex(degrees)];
    }

    public static int directionBucketIndex(double degrees) {
        double normalized = ((degrees % 360d) + 360d) % 360d;
        return (int) Math.round(normalized / 45d) % 8;
    }

    public static int precipitationIntensityBucket(double sumPrecipMm) {
        if (sumPrecipMm < 1d) {
            return PRECIP_INTENSITY_VERY_LIGHT;
        } else if (sumPrecipMm < 5d) {
            return PRECIP_INTENSITY_LIGHT;
        } else if (sumPrecipMm < 15d) {
            return PRECIP_INTENSITY_MODERATE;
        } else {
            return PRECIP_INTENSITY_HEAVY;
        }
    }

    public static int precipitationTypeBucket(
            double avgTempC,
            boolean anySnowCode,
            boolean anyThunderCode,
            boolean anyFreezingCode
    ) {
        if (anyThunderCode) {
            return PRECIP_TYPE_THUNDERSTORM_RAIN;
        } else if (anySnowCode || avgTempC <= 1.0) {
            return PRECIP_TYPE_SNOW;
        } else if (anyFreezingCode || (avgTempC > 1.0 && avgTempC < 3.0)) {
            return PRECIP_TYPE_RAIN_SNOW_MIX;
        } else {
            return PRECIP_TYPE_RAIN;
        }
    }
}
