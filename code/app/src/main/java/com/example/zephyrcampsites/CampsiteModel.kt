package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.google.firebase.firestore.FieldValue
import android.net.Uri
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Model class for Campsites. Also creates snapshot listener for self of the corresponding Firebase collection.
 *
 * @param firestore Database used for campsites and related registrations.
 * @param storage Optional Storage instance for local emulator tests.
 * @property campsites Local list of campsites, will be updated by snapshot listener
 * @property connector Instance of Firebase connector for campsite collection
 * @property campsiteListener Used to hold listener of Firebase, so listening can be canceled when needed.
 * @property error Holds error from Firebase if needed.
 * @property isLoading Whether the first campsite snapshot is still loading.
 */
class CampsiteModel(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val storage: FirebaseStorage? = null,
) : ObservableModel<CampsiteModel>() {
    val campsites = mutableStateListOf<Campsite>()
    val connector = FirebaseConnector("campsite", firestore)
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
        context: Context,
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
        imageUris: List<Uri> = emptyList(),
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

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val compressedImages = mutableListOf<String>()

                for (imageUri in imageUris) {
                    val compressedImage =
                        ImageCompressionUtil.compressImage(
                            context = context,
                            imageUri = imageUri
                        )

                    compressedImages.add(compressedImage)
                }

                newCampsite.compressedImages =
                    compressedImages.toList()

                val imageBytes = compressedImages.sumOf {
                    it.toByteArray(Charsets.UTF_8).size
                }

                require(imageBytes <= 700 * 1024) {
                    "Images are too large for this campsite document."
                }

                connector.add(
                    newCampsite,
                    onSuccess = {
                        notifyObservers(this@CampsiteModel)
                        onSuccess()
                    },
                    onFailure = onFailure
                )

            } catch (e: Exception) {
                onFailure(
                    "Failed to process campsite images: ${e.message}"
                )
            }
        }
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
     * The following function was generated by OpenAI, ChatGPT,
     * "Update campsite details using multiple Firestore fields",
     * 2026-11-06.
     */
    fun updateCampsiteDetails(
        context: Context,
        campsite: Campsite,
        input: CampsiteFormInput,
        geolocation: GeoPoint,
        startDate: Date,
        endDate: Date,
        imageUris: List<Uri> = emptyList(),
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val updatedFields = mutableMapOf<String, Any>(
            "name" to input.name.trim(),
            "location" to input.location.trim(),
            "description" to input.description.trim(),
            "maxOccupancy" to input.maxOccupancy.trim().toInt(),
            "pricePerNight" to input.pricePerNight.trim().toDouble(),
            "tags" to input.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            "rules" to input.rules.trim(),
            "toggle" to input.toggle,
            "geolocation" to geolocation,
            "bookingPeriodStart" to startDate,
            "bookingPeriodEnd" to endDate
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Declare outside the if block
                val newCompressedImages = mutableListOf<String>()

                if (imageUris.isNotEmpty()) {
                    for (imageUri in imageUris) {
                        val compressedImage =
                            ImageCompressionUtil.compressImage(
                                context = context,
                                imageUri = imageUri
                            )

                        newCompressedImages.add(compressedImage)
                    }
                }

                val reference = connector.db.document(campsite.id)

                firestore.runTransaction { transaction ->
                    val document = transaction.get(reference)

                    check(document.exists()) {
                        "Campsite no longer exists."
                    }

                    val currentImages =
                        (document.get("compressedImages") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?: emptyList()

                    val updatedImages = currentImages + newCompressedImages

                    val totalImageBytes = updatedImages.sumOf {
                        it.toByteArray(Charsets.UTF_8).size
                    }

                    require(totalImageBytes <= 700 * 1024) {
                        "Too many images. Please select fewer photos."
                    }

                    val fieldsToUpdate = updatedFields.toMutableMap()

                    if (newCompressedImages.isNotEmpty()) {
                        fieldsToUpdate["compressedImages"] = updatedImages
                    }

                    transaction.update(reference, fieldsToUpdate)

                    true
                }
                    .addOnSuccessListener {
                        notifyObservers(this@CampsiteModel)
                        onSuccess()
                    }
                    .addOnFailureListener { exception ->
                        onFailure(
                            exception.message ?: "Failed to update campsite."
                        )
                    }

            } catch (e: Exception) {
                onFailure(
                    "Failed to update campsite: ${e.message}"
                )
            }
        }
    }


    /**
     * Closes related registrations, removes waiting list entries and lotteries, then deletes the
     * campsite.
     *
     * @param target Campsite to remove.
     * @param onSuccess Called after Firestore confirms deletion.
     * @param onFailure Called if the campsite or related records cannot be removed.
     */
    fun deleteCampsite(
        target: Campsite,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        val fail: (Exception) -> Unit = { onFailure(it.message ?: "Failed to remove campsite.") }
        firestore
            .collection("lottery")
            .whereEqualTo("campsiteId", target.id)
            .get(Source.SERVER)
            .addOnSuccessListener { lotteries ->
                if (lotteries.size() > 499) {
                    onFailure("Too many related lotteries to remove at once.")
                    return@addOnSuccessListener
                }
                val closeBatch = firestore.batch()
                lotteries.documents.forEach {
                    closeBatch.update(it.reference, "registrationEnd", System.currentTimeMillis())
                }
                closeBatch
                    .commit()
                    .addOnSuccessListener {
                        val queries =
                            lotteries.documents.map { lottery ->
                                firestore
                                    .collection("waitingList")
                                    .whereEqualTo("lotteryId", lottery.id)
                                    .get(Source.SERVER)
                            }
                        Tasks.whenAllSuccess<QuerySnapshot>(queries)
                            .addOnSuccessListener { lists ->
                                val entries = lists.flatMap { it.documents }
                                if (entries.size + lotteries.size() + 1 > 500) {
                                    onFailure("Too many related records to remove at once.")
                                    return@addOnSuccessListener
                                }
                                val batch = firestore.batch()
                                entries.forEach { batch.delete(it.reference) }
                                lotteries.documents.forEach { batch.delete(it.reference) }
                                batch.delete(connector.db.document(target.id))
                                batch
                                    .commit()
                                    .addOnSuccessListener {
                                        notifyObservers(this)
                                        onSuccess()
                                    }
                                    .addOnFailureListener(fail)
                            }
                            .addOnFailureListener(fail)
                    }
                    .addOnFailureListener(fail)
            }
            .addOnFailureListener(fail)
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

    fun removeCompressedImage(
        campsite: Campsite,
        imageIndex: Int,
        expectedImage: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val reference = connector.db.document(campsite.id)

        firestore.runTransaction { transaction ->
            val document = transaction.get(reference)

            check(document.exists()) {
                "Campsite no longer exists."
            }

            val images = (document.get("compressedImages") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()

            check(imageIndex in images.indices) {
                "The selected image no longer exists. Refresh the list."
            }

            check(images[imageIndex] == expectedImage) {
                "The selected image has changed. Refresh the list."
            }

            val updatedImages = images.toMutableList()
            updatedImages.removeAt(imageIndex)

            transaction.update(
                reference,
                "compressedImages",
                updatedImages
            )

            true
        }
            .addOnSuccessListener {
                notifyObservers(this)
                onSuccess()
            }
            .addOnFailureListener(onError)
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

    /**
     * Removes one selected image and clears every matching reference on this campsite.
     *
     * @param campsite Campsite containing the selected image.
     * @param path Selected image path, kept separate from the mutable campsite fields.
     * @param onSuccess Called after Storage deletion and reference cleanup succeed.
     * @param onError Called if the image is stale, invalid, or cannot be removed.
     */
    fun removeImage(
        campsite: Campsite,
        path: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (path.isBlank()) {
            onError(IllegalArgumentException("No campsite image exists."))
            return
        }
        val reference = connector.db.document(campsite.id)
        reference
            .get(Source.SERVER)
            .addOnSuccessListener { document ->
                val promotional = document.get("promoImagePaths") as? List<*> ?: emptyList<Any>()
                if (
                    !document.exists() ||
                        (document.getString("imagePath") != path && path !in promotional)
                ) {
                    onError(
                        IllegalStateException(
                            "The selected campsite image has changed. Refresh the list."
                        )
                    )
                    return@addOnSuccessListener
                }
                connector.removeImage(
                    path,
                    storage ?: Firebase.storage,
                    onSuccess = {
                        firestore
                            .runTransaction { transaction ->
                                val current = transaction.get(reference)
                                check(current.exists()) { "Campsite no longer exists." }
                                val updates =
                                    mutableMapOf<String, Any>(
                                        "promoImagePaths" to FieldValue.arrayRemove(path)
                                    )
                                if (current.getString("imagePath") == path) {
                                    updates["imagePath"] = ""
                                }
                                transaction.update(reference, updates)
                                true
                            }
                            .addOnSuccessListener {
                                notifyObservers(this)
                                onSuccess()
                            }
                            .addOnFailureListener(onError)
                    },
                    onError = onError,
                )
            }
            .addOnFailureListener(onError)
    }
}
