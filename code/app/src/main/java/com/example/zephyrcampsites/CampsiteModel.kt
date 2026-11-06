package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
/**
 * Model class for Campsites. Also creates snapshot listener for self of the corresponding Firebase collection.
 *
 * @property campsites Local list of campsites, will be updated by snapshot listener
 * @property connector Instance of Firebase connector for campsite collection
 * @property campsiteListener Used to hold listener of Firebase, so listening can be canceled when needed.
 * @property error Holds error from Firebase if needed.
 * @property isLoading Whether the first campsite snapshot is still loading.
 */
class CampsiteModel : ObservableModel<CampsiteModel>() {
    val campsites = mutableStateListOf<Campsite>()
    val connector = FirebaseConnector("campsite")
    private val waitlistConnector = FirebaseConnector("waitingList")
    private val lotteryConnector = FirebaseConnector("lottery")
    private var campsiteListener: ListenerRegistration? = null
    var error: FirebaseFirestoreException? by mutableStateOf(null)
        private set

    var isLoading: Boolean by mutableStateOf(true)
        private set

    /**
     * Registers a new campsite for an owner
     *
     * @param ownerId ID of the owner registering the campsite
     * @param name Name of the campsite
     * @param location Location of the campsite
     * @param description Description of the campsite
     * @param maxOccupancy Maximum occupancy of the campsite
     * @param pricePerNight Price per night
     * @param tags Tags for the campsite
     * @param rules Rules & cancellation policy
     * @param toggle if entrants must accept rules before reserving
     * @param onSuccess Called after the campsite is saved.
     * @param onFailure Called if the campsite cannot be saved.
     */
    fun registerCampsite(
        ownerId: String,
        name: String,
        location: String,
        description: String,
        maxOccupancy: Int,
        pricePerNight: Double,
        tags: List<String>,
        rules: String,
        toggle: Boolean,
        geolocation: GeoPoint,
        startDate: Date,
        endDate: Date,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val nextId = (campsites.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0) + 1
        val newCampsite = Campsite(nextId.toString())
        newCampsite.ownerID = ownerId
        newCampsite.name = name
        newCampsite.location = location
        newCampsite.description = description
        newCampsite.maxOccupancy = maxOccupancy
        newCampsite.pricePerNight = pricePerNight
        newCampsite.tags = tags
        newCampsite.rules = rules
        newCampsite.toggle = toggle
        newCampsite.geolocation = geolocation
        newCampsite.bookingPeriodStart = startDate
        newCampsite.bookingPeriodEnd = endDate

        connector.add(newCampsite,
            onSuccess = {
                notifyObservers(this)
                onSuccess()
            },
            onFailure = onFailure
        )
    }

    /**
     * Updates campsite given object of type Campsite.
     * Changes (given) field to (given) value.
     * Caller needs to handle IllegalStateException in case of failure to communicate with Firestore.
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
     * Deletes a given campsite, closes active lotteries/waitlist, and notifies affected users.
     * Caller needs to handle IllegalStateException in case of failure to communicate with Firestore.
     *
     * @param target Campsite to delete.
     * @param onSuccess Called when successfully deleted campsite from Firebase.
     * @param onFailure Called when failed to delete campsite from Firebase.
     */

    fun deleteCampsite(
        target: Campsite,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val db = Firebase.firestore
        val lotteryConnector = FirebaseConnector("lottery")
        val waitingListConnector = FirebaseConnector("waitingList")

        // Find lotteries associated with the campsite.
        lotteryConnector.db
            .whereEqualTo("campsiteId", target.id)
            .get()
            .addOnSuccessListener { lotteries ->

                val lotteryDocs = lotteries.documents

                // Close all associated lotteries.
                val closeBatch = db.batch()

                lotteryDocs.forEach { lottery ->
                    closeBatch.update(
                        lottery.reference,
                        "registrationEnd",
                        System.currentTimeMillis()
                    )
                }

                closeBatch.commit()
                    .addOnSuccessListener {

                        val documentsToDelete =
                            mutableListOf<DocumentReference>()

                        // Find waiting-list entries for each lottery.
                        fun loadWaitingLists(index: Int) {

                            if (index >= lotteryDocs.size) {

                                // Include lottery documents.
                                lotteryDocs.forEach { lottery ->
                                    documentsToDelete.add(lottery.reference)
                                }

                                // Delete related documents.
                                val deleteBatch = db.batch()

                                documentsToDelete.forEach { document ->
                                    deleteBatch.delete(document)
                                }

                                deleteBatch.commit()
                                    .addOnSuccessListener {

                                        // Delete campsite last.
                                        connector.remove(
                                            target,
                                            onSuccess = onSuccess,
                                            onFailure = onFailure
                                        )
                                    }
                                    .addOnFailureListener { error ->
                                        onFailure(
                                            error.message ?: "Cleanup failed."
                                        )
                                    }

                                return
                            }

                            waitingListConnector.db
                                .whereEqualTo(
                                    "lotteryId",
                                    lotteryDocs[index].id
                                )
                                .get()
                                .addOnSuccessListener { waitingLists ->

                                    waitingLists.documents.forEach { document ->
                                        documentsToDelete.add(document.reference)
                                    }

                                    loadWaitingLists(index + 1)
                                }
                                .addOnFailureListener { error ->
                                    onFailure(
                                        error.message
                                            ?: "Failed to load waiting lists."
                                    )
                                }
                        }

                        loadWaitingLists(0)
                    }
                    .addOnFailureListener { error ->
                        onFailure(
                            error.message ?: "Failed to close lotteries."
                        )
                    }
            }
            .addOnFailureListener { error ->
                onFailure(
                    error.message ?: "Failed to load lotteries."
                )
            }
    }


    /**
     * Gets a campsite's image.
     *
     * @param path String of path of the image
     */
    fun getCampsiteImage(
        path: String
    ) : String {
        var result = ""
        connector.resourceGetter(path = path, result = { uri ->
            result = uri
        })
        return result
    }

    /**
     * Retrieves a campsite image URL asynchronously.
     *
     * @param path HTTPS URL or Firebase Storage path of the image.
     * @param onResult Called when the image URL is available.
     * @param onError Called if the image URL cannot be retrieved.
     */
    fun getCampsiteImage(
        path: String,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        connector.resourceGetter(path = path, result = onResult, onError = onError)
    }

    /**
     * Stops the listener to the Firebase Firestore.
     */
    fun stopListening() {
        campsiteListener?.remove()
        campsiteListener = null
    }

    init {
        campsiteListener = connector.initializeListener(
            localList = campsites,
            type = Campsite::class.java,
            onError = { thrownError ->
                error = thrownError
                isLoading = false
            },
            onSuccess = {
                error = null
                isLoading = false
            }
        )
    }
}
