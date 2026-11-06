package com.example.zephyrcampsites

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Verifies map marker coordinates and missing-location handling without network requests. */
class CampsiteMapLocationTest {
    @Test
    fun markerUsesLatitudeLongitudeAndBoundsUseLongitudeLatitude() {
        assertEquals(
            "https://www.openstreetmap.org/export/embed.html?bbox=-118.120000,52.850000,-118.100000,52.870000" +
                "&layer=mapnik&marker=52.860000,-118.110000",
            CampsiteMapLocation.embedUrl(52.86, -118.11)
        )
    }

    @Test
    fun legacyPlaceholderAndInvalidCoordinatesHaveNoMap() {
        assertNull(CampsiteMapLocation.embedUrl(0.0, 0.0))
        assertNull(CampsiteMapLocation.embedUrl(Double.NaN, -118.0))
        assertNull(CampsiteMapLocation.embedUrl(53.0, Double.POSITIVE_INFINITY))
        assertNull(CampsiteMapLocation.embedUrl(91.0, -118.0))
        assertNull(CampsiteMapLocation.embedUrl(53.0, -181.0))
    }

    @Test
    fun oneZeroCoordinateIsNotMistakenForTheLegacyPlaceholder() {
        assertNotNull(CampsiteMapLocation.embedUrl(0.0, 30.0))
        assertNotNull(CampsiteMapLocation.embedUrl(53.0, 0.0))
    }

    @Test
    fun boundingBoxStaysWithinCoordinateLimits() {
        val url = CampsiteMapLocation.embedUrl(89.999, 179.999)!!
        assertTrue(url.contains("bbox=179.989000,89.989000,180.000000,90.000000"))
    }

    @Test
    fun localeDoesNotChangeUrlDecimalSeparators() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertTrue(CampsiteMapLocation.embedUrl(52.86, -118.11)!!.endsWith("marker=52.860000,-118.110000"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
