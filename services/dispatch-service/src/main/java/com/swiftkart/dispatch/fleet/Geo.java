package com.swiftkart.dispatch.fleet;

/** Small geometry helpers on the WGS84 sphere. */
public final class Geo {

    /** Riders ride roads, not straight lines: road distance ≈ 1.4 × crow-flies in Indian cities. */
    public static final double ROAD_FACTOR = 1.4;
    public static final double RIDER_KMPH = 20;

    private Geo() {
    }

    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Simulated seconds to ride between two points. */
    public static long rideSeconds(double lat1, double lon1, double lat2, double lon2) {
        return Math.round(km(lat1, lon1, lat2, lon2) * ROAD_FACTOR / RIDER_KMPH * 3600);
    }

    /** Moves a point toward a target by at most {@code stepKm} of straight-line distance. */
    public static double[] step(double lat, double lon, double toLat, double toLon, double stepKm) {
        double d = km(lat, lon, toLat, toLon);
        if (d <= stepKm || d == 0) return new double[]{toLat, toLon};
        double f = stepKm / d;
        return new double[]{lat + (toLat - lat) * f, lon + (toLon - lon) * f};
    }
}
