/**
 * This file only contains the data class for campsites.
 */

package com.example.zephyrcampsites

import java.util.Date

/**
 * Data class for an individual campsite.
 *
 * No methods, only data holder. ID is temporarily given to create, and everything is var so owners can modify.
 *
 * @param id the campsite id to assign to this instance.
 */
data class Campsite (val id: Int) {
    var name: String = ""
    var location: String = ""
    var description: String = ""
    var maxOccupancy: Int = 0
    var geolocation: String = "" // type is placeholder, could look at https://developers.google.com/codelabs/maps-platform/maps-platform-101-android
    var imagePath: String = ""
    var tags: List<String> = listOf("")
    var ownerID: String = ""
    var bookingPeriodStart: Date = Date()
    var bookingPeriodEnd: Date = Date()
    var rating: Float = 0.0f
    var rulesPolicies: List<String> = listOf("")
    var promoImagePaths: List<String> = listOf("")
}