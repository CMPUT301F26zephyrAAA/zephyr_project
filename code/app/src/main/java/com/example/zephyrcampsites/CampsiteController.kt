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

    /**
     * Adds campsite to model.
     * Should use proper params in the future, but currently just adds placeholders in fields.
     */
    fun addCampsite() {
        model.createCampsite("testing", "here", "the thing", 20)
    }

    /**
     * Search function for specified filters.
     * All params can be null if filter was not used.
     * Calls on helper functions to do actual checks, this function only checks that all checks were true.
     *
     * @param userLocation User device location.
     * @param distanceLimit Distance limit that a campsite can be from user device location
     * @param startDate User set starting date of range for intended booking
     * @param endDate User set ending date of range for intended booking
     * @param persons User set number of guests
     * @param string User search string for both campsite name and location
     * @return List of campsites that meets all criteria that were specified.
     */
    fun search(userLocation: Location?, distanceLimit : Int?, startDate : Date?, endDate : Date?, persons : Int?, string: String) : List<Campsite> {
        val resultList : MutableList<Campsite> = mutableListOf()
        model.campsites.forEach { campsite ->
            if (campsite.id.toInt() == 1) {
                println(campsite.bookingPeriodStart)
                println(startDate)
            }
            val campsiteDate : Pair<Date, Date> = Pair(campsite.bookingPeriodStart, campsite.bookingPeriodEnd)
            val campsiteLocConvert = Location("manual").apply {
                latitude = campsite.geolocation.latitude
                longitude = campsite.geolocation.longitude
            }
            // TODO: WHEN BOOKING MODEL IS DONE: Modify logic to check date first, then query for max amount of occupants within date range and pass that to guestsCheck
            if (
                regexCheck(string, campsite.name, campsite.location) &&
                locationCheck(campsiteLocConvert, userLocation, distanceLimit) &&
                bookDateCheck(startDate, endDate, campsiteDate) &&
                guestsCheck(campsite.maxOccupancy, 0, persons) //0 for now, check bookings for total bookers later
            ) {
                resultList.add(campsite)
            }
        }
        return resultList
    }

    fun imageGetter(id: Int) : String {
        return model.getCampsiteImage("campsite/$id.png")
    }

    /**
     * Private helper function to check for partial, case-insensitive match.
     * Will return true if search is completely empty.
     *
     * @param search String to search for
     * @param name Name string to search within
     * @param location Location string to search within
     * @return Type boolean of if search is a (partial, case-insensitive) match with either name or location.
     */
    private fun regexCheck(search : String, name : String, location : String) : Boolean {
        if (search == "") {
            return true
        }
        val regex = Regex(pattern = search, option = RegexOption.IGNORE_CASE)
        return (regex.containsMatchIn((name)) || regex.containsMatchIn(location))
    }

    /**
     * Private helper function to check if campsite location is within limit radius of user location.
     * Will return true if either user location or limit is null.
     *
     * @param campsite Location of target campsite
     * @param user Location of user searching
     * @param limit Limit distance radius
     * @return Type boolean of if campsite is within limit radius
     */
    private fun locationCheck( campsite: Location, user: Location?, limit: Int?) : Boolean {
        if (user == null || limit == null) {
            return true
        }
        return (campsite.distanceTo(user) < limit)
    }

    /**
     * Private helper function to check if campsite is available for booking within desired date range.
     * Checks for dates only, not capacity.
     * Will return true if either end of desired date range is null.
     *
     * @param startDate Start of desired date range
     * @param endDate End of desired date range
     * @param campsiteDate Pair<Date, Date> of campsite's booking period start and end
     * @return Type boolean of if campsite is available for booking during range
     */
    private fun bookDateCheck( startDate: Date?, endDate: Date?, campsiteDate: Pair<Date, Date>) : Boolean {
        if (startDate == null || endDate == null) {
            return true
        }
        return (campsiteDate.first <= startDate && campsiteDate.second >= endDate)
    }

    /**
     * Private helper function to check if campsite can hold amount of prospective guests.
     * Checks for capacity only, not dates.
     * Will return true if guest amount of null.
     *
     * @param max Maximum occupancy of target campsite
     * @param occupied Amount of guests already booked
     * @param guests Amount of prospective guests
     * @return Type boolean of if campsite can hold amount of guests.
     */
    private fun guestsCheck( max: Int, occupied: Int, guests: Int?) : Boolean {
        if (guests == null) {
            return true
        }
        return (max - occupied >= guests)
    }

}