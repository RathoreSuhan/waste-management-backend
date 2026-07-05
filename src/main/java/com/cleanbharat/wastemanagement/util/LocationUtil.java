package com.cleanbharat.wastemanagement.util;

/**
 * Utility class for
 * normalizing location names.
 */
public final class LocationUtil {

    // Prevent object creation
    private LocationUtil() {
    }

    /**
     * Converts location names into
     * proper title case.
     *
     * Example:
     * "new delhi" -> "New Delhi"
     * "  south goa " -> "South Goa"
     */
    public static String normalizeLocation(String location) {

        String[] words = location.trim().toLowerCase().split("\\s+");

        StringBuilder normalized = new StringBuilder();

        for (String word : words) {

            normalized.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }

        return normalized.toString().trim();
    }
}