package com.cleanbharat.wastemanagement.util;

import lombok.experimental.UtilityClass;

/**
 * Utility methods for geographic calculations.
 *
 * Used for:
 * - Duplicate report detection
 * - Nearby report search
 * - Nearest cleaner search (future)
 * - Geo analytics (future)
 */
@UtilityClass
public class GeoLocationUtil {

    /**
     * Average radius of Earth in meters.
     */
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * Average meters represented by
     * one degree of latitude.
     */
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

    /**
     * Calculates the exact distance
     * between two GPS coordinates
     * using the Haversine formula.
     *
     * @return distance in meters
     */
    public double calculateDistanceMeters(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2) {

        double latitudeDifference =
                Math.toRadians(latitude2 - latitude1);

        double longitudeDifference =
                Math.toRadians(longitude2 - longitude1);

        double latitude1Radians =
                Math.toRadians(latitude1);

        double latitude2Radians =
                Math.toRadians(latitude2);

        double haversine =
                Math.sin(latitudeDifference / 2)
                        * Math.sin(latitudeDifference / 2)
                        +
                        Math.cos(latitude1Radians)
                                * Math.cos(latitude2Radians)
                                * Math.sin(longitudeDifference / 2)
                                * Math.sin(longitudeDifference / 2);

        double centralAngle =
                2 * Math.atan2(
                        Math.sqrt(haversine),
                        Math.sqrt(1 - haversine)
                );

        return EARTH_RADIUS_METERS * centralAngle;
    }

    /**
     * Calculates latitude delta
     * for the given search radius.
     *
     * Used while building
     * the SQL bounding box.
     */
    public double calculateLatitudeDelta(double radiusMeters) {
        return radiusMeters / METERS_PER_DEGREE_LATITUDE;
    }

    /**
     * Calculates longitude delta for the given search radius.
     * Longitude distance changes depending on latitude.
     */
    public double calculateLongitudeDelta(double latitude, double radiusMeters) {

        double metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude));

        return radiusMeters / metersPerDegreeLongitude;
    }

}