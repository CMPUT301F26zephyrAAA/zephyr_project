package com.example.zephyrcampsites

import android.location.Location
import java.util.Date

data class UserFilter(
    var searchString: String = "",
    var deviceLocation: Location? = null,
    var distanceLimit: Int? = Int.MAX_VALUE,
    var startDate: Date? = Date(),
    var endDate: Date? = Date(),
    var guests: Int? = 1
) {
    fun reset() {
        searchString = ""
        deviceLocation = null
        distanceLimit = Int.MAX_VALUE
        startDate = Date()
        endDate = Date()
        guests = 1
    }


}