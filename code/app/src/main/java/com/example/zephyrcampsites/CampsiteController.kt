package com.example.zephyrcampsites

import android.location.Location
import java.util.Date

/**
 * Controller class for anything campsite related.
 * Will handle any user interactions made on a view.
 * (Methods should be called from view)
 *
 * @param model Model to operate on.
 */
class CampsiteController(val model: CampsiteModel) {
    fun onClick() {
        model.createCampsite("add test", "right here", "the thing", 20)
        model.campsites.forEach { campsite ->
            println(campsite.id)
        }
    }

    /**
     * Retrieve campsite entry based on ID.
     *
     * @param id ID of campsite to retrieve
     * @return Full campsite object
     */
    fun retrieveId(id: Int) : Campsite? {
        return model.campsites.find { it.id.toInt() == id }
    }

    fun addCampsite() {
        model.createCampsite("testing", "here", "the thing", 20)
    }

    //Pair<Float, Float>, Date, Date, Int, String
    fun search(userLocation: Location?, distanceLimit : Int, startDate : Date, endDate : Date, persons : Int, string: String) : List<Campsite> {
        val regex = Regex(pattern = string, option = RegexOption.IGNORE_CASE)
        val resultList : MutableList<Campsite> = mutableListOf()
        model.campsites.forEach { campsite ->
            println(campsite.id)
            if (regex.containsMatchIn((campsite.name)) || regex.containsMatchIn(campsite.location)) {
                println("pass regex")
                if (userLocation != null) {
                    println("user has location")
                    val campsiteLocConvert = Location("manual").apply {
                        latitude = campsite.geolocation.latitude
                        longitude = campsite.geolocation.longitude
                    }
                    if (campsiteLocConvert.distanceTo(userLocation) < distanceLimit) {
                        println("passed location check")
                        println(userLocation)
                        if (campsite.bookingPeriodStart < startDate && campsite.bookingPeriodEnd > endDate) {
                            println("passed date check")
                            if (campsite.maxOccupancy > persons) { // Once booking is done: campsite.maxOccupancy - [campsite bookings] > persons
                                println("passed occupancy test")
                                resultList.add(campsite)
                            }
                        }
                    }
                } else {
                    println("user has no location")
                    if (campsite.bookingPeriodStart < startDate && campsite.bookingPeriodEnd > endDate) {
                        println("passed date check")
                        if (campsite.maxOccupancy > persons) { // Once booking is done: campsite.maxOccupancy - [campsite bookings] > persons
                            println("passed occupancy test")
                            resultList.add(campsite)
                        }
                    }
                }
            }
        }
        return resultList
    }
}