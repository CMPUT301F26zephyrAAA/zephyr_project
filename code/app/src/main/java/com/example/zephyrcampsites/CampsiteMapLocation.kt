package com.example.zephyrcampsites

import java.util.Locale

/** Builds map URLs from saved coordinates, treating the legacy (0, 0) default as missing. */
object CampsiteMapLocation {
    /**
     * Creates an OpenStreetMap embed URL with a marker at the campsite.
     *
     * @param latitude Saved latitude in degrees.
     * @param longitude Saved longitude in degrees.
     * @return Map URL, or null for missing or invalid coordinates.
     */
    fun embedUrl(latitude: Double, longitude: Double): String? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0 || (latitude == 0.0 && longitude == 0.0)) {
            return null
        }
        val west = coordinate((longitude - 0.01).coerceAtLeast(-180.0))
        val east = coordinate((longitude + 0.01).coerceAtMost(180.0))
        val south = coordinate((latitude - 0.01).coerceAtLeast(-90.0))
        val north = coordinate((latitude + 0.01).coerceAtMost(90.0))
        return "https://www.openstreetmap.org/export/embed.html?bbox=$west,$south,$east,$north" +
            "&layer=mapnik&marker=${coordinate(latitude)},${coordinate(longitude)}"
    }

    /** Formats a URL coordinate with a decimal point regardless of the device language. */
    private fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
}
