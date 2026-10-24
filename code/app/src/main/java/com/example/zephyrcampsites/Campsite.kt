package com.example.zephyrcampsites

import com.google.firebase.firestore.GeoPoint
import java.util.Date

/**
 * Data class for an individual campsite.
 *
 * No methods, only data holder. ID is given to create, and everything is var so owners can modify.
 *
 * @param id the campsite id to assign to this instance.
 */
data class Campsite (override val id: String = "0") : DataHolder(id) {
    override var name: String = ""
    var location: String = ""
    var description: String = ""
    var maxOccupancy: Int = 0
    var geolocation: GeoPoint = GeoPoint(0.0, 0.0) // type is placeholder with (latitude, longitude), could look at https://developers.google.com/codelabs/maps-platform/maps-platform-101-android
    var imagePath: String = ""
    var tags: List<String> = listOf("")
    var ownerID: String = ""
    var bookingPeriodStart: Date = Date(1)
    var bookingPeriodEnd: Date = Date(1793723395)
    var rating: MutableList<Int> = mutableListOf()
    var rulesPolicies: List<String> = listOf("")
    var promoImagePaths: List<String> = listOf("")
}