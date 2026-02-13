package com.campertools.app.weather;

public final class WeatherNowSnapshot {
    public final double currentTempC;
    public final double currentWindKmh;
    public final double currentWindDirectionDeg;
    public final double minTempC;
    public final double maxTempC;
    public final double avgTempC;
    public final double sumPrecipMm;
    public final boolean anyPrecip;
    public final boolean anySnowCode;
    public final boolean anyThunderCode;
    public final boolean anyFreezingCode;

    public WeatherNowSnapshot(
            double currentTempC,
            double currentWindKmh,
            double currentWindDirectionDeg,
            double minTempC,
            double maxTempC,
            double avgTempC,
            double sumPrecipMm,
            boolean anyPrecip,
            boolean anySnowCode,
            boolean anyThunderCode,
            boolean anyFreezingCode
    ) {
        this.currentTempC = currentTempC;
        this.currentWindKmh = currentWindKmh;
        this.currentWindDirectionDeg = currentWindDirectionDeg;
        this.minTempC = minTempC;
        this.maxTempC = maxTempC;
        this.avgTempC = avgTempC;
        this.sumPrecipMm = sumPrecipMm;
        this.anyPrecip = anyPrecip;
        this.anySnowCode = anySnowCode;
        this.anyThunderCode = anyThunderCode;
        this.anyFreezingCode = anyFreezingCode;
    }
}
