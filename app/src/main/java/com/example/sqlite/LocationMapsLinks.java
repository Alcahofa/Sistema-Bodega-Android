package com.example.sqlite;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pure URL helpers for map and directions fallbacks. */
final class LocationMapsLinks {
    private LocationMapsLinks() { }

    static boolean isValidCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    static String destination(double latitude, double longitude) {
        requireValidCoordinate(latitude, longitude);
        return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    static String mapUrl(double latitude, double longitude) {
        return "https://www.google.com/maps/search/?api=1&query="
                + encode(destination(latitude, longitude));
    }

    static String mapUrl(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("A destination is required");
        }
        return "https://www.google.com/maps/search/?api=1&query=" + encode(address.trim());
    }

    static String directionsUrl(double destinationLatitude, double destinationLongitude) {
        return "https://www.google.com/maps/dir/?api=1&destination="
                + encode(destination(destinationLatitude, destinationLongitude));
    }

    static String directionsUrl(double originLatitude, double originLongitude,
                                double destinationLatitude, double destinationLongitude) {
        return "https://www.google.com/maps/dir/?api=1&origin="
                + encode(destination(originLatitude, originLongitude))
                + "&destination="
                + encode(destination(destinationLatitude, destinationLongitude));
    }

    static String directionsUrl(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("A destination is required");
        }
        return "https://www.google.com/maps/dir/?api=1&destination=" + encode(address.trim());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void requireValidCoordinate(double latitude, double longitude) {
        if (!isValidCoordinate(latitude, longitude)) {
            throw new IllegalArgumentException("Coordinates are outside valid ranges");
        }
    }
}
