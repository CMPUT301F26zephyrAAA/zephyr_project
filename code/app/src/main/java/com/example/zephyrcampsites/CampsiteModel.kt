package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.firestore.GeoPoint

/**
 * Model class for Campsites. Also creates snapshot listener for self of the corresponding Firebase collection.
 *
 * @property campsites Local list of campsites, will be updated by snapshot listener
 * @property connector Instance of Firebase connector for campsite collection
 */
class CampsiteModel : ObservableModel<CampsiteModel>() {
    val campsites = mutableStateListOf(
        Campsite("1"),
        Campsite("2")
    )
    val connector = FirebaseConnector("campsite")

    /**
     * Creates campsite given name, location, description, and max occupancy. (Check required fields?)
     * Uses Campsite data class and adds it using connector.
     *
     * @param name Name of new campsite
     * @param location Location of new campsite (unsure if redundant if geolocation is a thing?)
     * @param description Description of new campsite
     * @param maxOccupancy Int of max occupancy of new campsite
     */
    fun createCampsite(
        name: String,
        location: String,
        description: String,
        maxOccupancy: Int,
    ) {
        val newCampsite = Campsite((campsites.size + 1).toString())
        newCampsite.name = name
        newCampsite.location = location
        newCampsite.description = description
        newCampsite.maxOccupancy = maxOccupancy
        connector.add(newCampsite)
        notifyObservers(this)
    }

    /**
     * Registers a new campsite for an owner
     *
     * @param ownerId ID of the owner registering the campsite
     * @param name Name of the campsite
     * @param location Location of the campsite
     * @param longitude Longitude of the campsite
     * @param latitude Latitude of the campsite
     * @param description Description of the campsite
     * @param maxOccupancy Maximum occupancy of the campsite
     * @param tags Tags for the campsite
     */
    fun registerCampsite(
        ownerId: String,
        name: String,
        location: String,
        longitude: Double,
        latitude: Double,
        description: String,
        maxOccupancy: Int,
        tags: List<String>
    ) {
        val newCampsite = Campsite((campsites.size + 1).toString())
        newCampsite.ownerID = ownerId
        newCampsite.name = name
        newCampsite.location = location
        newCampsite.geolocation = GeoPoint(latitude, longitude)
        newCampsite.description = description
        newCampsite.maxOccupancy = maxOccupancy
        newCampsite.tags = tags
        connector.add(newCampsite)
        notifyObservers(this)
    }

    /**
     * Updates campsite given object of type Campsite.
     * Changes (given) field to (given) value.
     *
     * @param target Campsite to update
     * @param field Field of campsite to update
     * @param value Value to update to
     */
    fun updateCampsite(
        target: Campsite,
        field: String,
        value: String
    ) {
        connector.update(target, field, value)
        notifyObservers(this)
    }

    /**
     * Deletes a given campsite.
     *
     * @param target Campsite to delete.
     */
    fun deleteCampsite(
        target: Campsite
    ) {
        connector.remove(target)
        notifyObservers(this)
    }

    fun getCampsiteImage(
        path: String
    ) : String {
        return connector.resourceGetter(path)
    }

    init {
        connector.initializeListener(campsites, Campsite::class.java)
    }
}