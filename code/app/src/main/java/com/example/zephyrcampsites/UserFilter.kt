package com.example.zephyrcampsites

import android.location.Location
import java.util.Date

/**
 * Data class for storing user filters in SummaryView.
 * Everything besides searchString is nullable.
 *
 * @param searchString String to search campsites by, case-insensitive. Should be set to empty string if not searching for anything.
 * @param deviceLocation Queried user device location. Defaults to null.
 * @param distanceLimit Radius to search for campsites within, from user (device) location. Defaults to maximum integer.
 * @param startDate User set start date for booking date filter, defaults to current date.
 * @param endDate User set end date for booking date filter, defaults to current date.
 * @param guests Amount of guests that user set to filter for, defaults to 1 but can be set to null (unsure if ever needed, or should just reset to 1 always).
 */
data class UserFilter(
    var searchString: String = "",
    var deviceLocation: Location? = null,
    var distanceLimit: Int? = Int.MAX_VALUE,
    var startDate: Date? = Date(),
    var endDate: Date? = Date(),
    var guests: Int? = 1
) {
    /**
     * Resets user filter back to default state.
     */
    fun reset() {
        searchString = ""
        deviceLocation = null
        distanceLimit = Int.MAX_VALUE
        startDate = Date()
        endDate = Date()
        guests = 1
    }
}