package com.mirrorsoul.mirrorsoul_api.region;

import java.math.BigDecimal;

public final class GeoDistanceUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistanceUtils() {
    }

    public static double distanceKm(
            BigDecimal firstLatitude,
            BigDecimal firstLongitude,
            BigDecimal secondLatitude,
            BigDecimal secondLongitude
    ) {
        double firstLatitudeRadians = Math.toRadians(firstLatitude.doubleValue());
        double secondLatitudeRadians = Math.toRadians(secondLatitude.doubleValue());
        double latitudeDifference = secondLatitudeRadians - firstLatitudeRadians;
        double longitudeDifference = Math.toRadians(
                secondLongitude.doubleValue() - firstLongitude.doubleValue()
        );

        double haversine = Math.pow(Math.sin(latitudeDifference / 2.0), 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians)
                * Math.pow(Math.sin(longitudeDifference / 2.0), 2);
        return 2.0 * EARTH_RADIUS_KM
                * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }
}
