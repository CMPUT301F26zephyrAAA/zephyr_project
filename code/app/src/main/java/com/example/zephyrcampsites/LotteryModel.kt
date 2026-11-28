// Persists lottery settings and observes lottery records in Firestore.

package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID

/**
 * Manages campsite lotteries and connects them to Firebase.
 */
class LotteryModel : ObservableModel<LotteryModel>() {

    val lotteries = mutableStateListOf<Lottery>()

    private val connector = FirebaseConnector("lottery")

    /**
     * Creates a lottery for a campsite.
     */
    fun createLottery(
        lotteryName: String,
        campsiteId: String,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int = 1,
        waitlistCapacity: Int?,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ): String {
        LotteryValidator().validateCampsiteId(campsiteId)

        // validate the lottery name
        require(lotteryName.isNotBlank()) {
            "Enter a lottery name."
        }

        // Registration must start in the future.
        LotteryValidator().validateFutureRegistrationStart(
            registrationStart,
            System.currentTimeMillis()
        )

        LotteryValidator().validateRegistrationPeriod(
            registrationStart,
            registrationEnd
        )

        LotteryValidator().validateBookingPeriod(
            bookingStart,
            bookingEnd
        )

        // Booking must start after registration ends.
        require(bookingStart > registrationEnd) {
            "Booking must start after registration ends."
        }

        // Check for overlapping booking periods for the same campsite.
        lotteries
            .filter { other ->
                other.campsiteId == campsiteId
            }
            .forEach { other ->
                LotteryValidator().validateNoBookingOverlap(
                    bookingStart,
                    bookingEnd,
                    other.bookingStart,
                    other.bookingEnd
                )
            }

        require(numberOfWinners > 0) {
            "Number of winners must be positive."
        }
        require(waitlistCapacity == null || waitlistCapacity > 0) {
            "Waitlist capacity must be positive."
        }


        val lottery = Lottery(UUID.randomUUID().toString())
        lottery.lotteryName = lotteryName.trim()

        lottery.campsiteId = campsiteId
        lottery.registrationStart = registrationStart
        lottery.registrationEnd = registrationEnd
        lottery.bookingStart = bookingStart
        lottery.bookingEnd = bookingEnd
        lottery.numberOfWinners = numberOfWinners
        lottery.waitlistCapacity = waitlistCapacity
        lottery.entrantCount = 0L
        // A new lottery has not started its winner draw yet.
        lottery.drawCompleted = false
        lottery.drawCompletedAt = 0L
        lottery.drawStatus = "PENDING"

        // No draw has started for this lottery yet.
        lottery.drawStartedAt = 0L

        // Save the lottery and confirm the result.
        connector.addWithCallback(
            value = lottery,
            onSuccess = {
                onSuccess(lottery.id)
            },
            onFailure = { exception ->
                onFailure(exception)
            }
        )
        return lottery.id
    }


    /**
     * Updates the registration period of an existing lottery.
     *
     * Registration dates can only be changed before registration starts.
     *
     * @param lotteryId ID of the lottery to update.
     * @param newStart New registration start time in milliseconds.
     * @param newEnd New registration end time in milliseconds.
     */
    fun updateRegistrationPeriod(
        lotteryId: String,
        newStart: Long,
        newEnd: Long
    ) {
        // Find the existing lottery.
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")

        // Registration dates can only be updated before registration starts.
        LotteryValidator().validateRegistrationNotStarted(
            lottery.registrationStart,
            System.currentTimeMillis()
        )

        // Make sure the new registration period is valid and in the future.
        require(newStart > System.currentTimeMillis() && newStart < newEnd) {
            "Enter a valid future registration period."
        }

        // Registration must end before the booking period starts.
        LotteryValidator().validateRegistrationBeforeBooking(
            newEnd,
            lottery.bookingStart
        )

        // Update the registration dates.
        lottery.registrationStart = newStart
        lottery.registrationEnd = newEnd

        // Save the updated registration dates to Firebase.
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("lottery")
            .document(lotteryId)
            .update(
                mapOf(
                    "registrationStart" to newStart,
                    "registrationEnd" to newEnd
                )
            )
    }

    /**
     * Updates the booking period before booking starts.
     */
    fun updateBookingPeriod(
        lotteryId: String,
        newStart: Long,
        newEnd: Long
    ) {
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")

        // Booking dates can only be changed before registration starts.
        LotteryValidator().validateRegistrationNotStarted(
            lottery.registrationStart,
            System.currentTimeMillis()
        )

        // Check the new booking dates.
        require(newStart > lottery.registrationEnd) {
            "Booking must start after registration ends."
        }

        LotteryValidator().validateBookingPeriod(newStart, newEnd)

        // Check for overlapping booking periods for the same campsite.
        lotteries
            .filter { other ->
                other.id != lotteryId &&
                        other.campsiteId == lottery.campsiteId
            }
            .forEach { other ->
                LotteryValidator().validateNoBookingOverlap(
                    newStart,
                    newEnd,
                    other.bookingStart,
                    other.bookingEnd
                )
            }

        require(newStart > System.currentTimeMillis()) {
            "Booking start must be in the future."
        }

        lottery.bookingStart = newStart
        lottery.bookingEnd = newEnd

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("lottery")
            .document(lotteryId)
            .update(
                mapOf(
                    "bookingStart" to newStart,
                    "bookingEnd" to newEnd
                )
            )
    }

    /**
     * Updates the number of winners before registration starts.
     */
    fun updateNumberOfWinners(lotteryId: String, newCount: Int) {
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")

        // Winner count can only be changed before registration starts.
        LotteryValidator().validateRegistrationNotStarted(
            lottery.registrationStart,
            System.currentTimeMillis()
        )

        // Check that the winner count is positive.
        LotteryValidator().validateNumberOfWinners(newCount)

        lottery.numberOfWinners = newCount

        // Save the updated value to Firebase.
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("lottery")
            .document(lotteryId)
            .update("numberOfWinners", newCount)
    }
    /**
     * Updates the waitlist capacity for a lottery.
     */
    fun updateWaitlistCapacity(lotteryId: String, newCapacity: Int?) {
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")

        val firestore =
            com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val lotteryRef = firestore.collection("lottery").document(lotteryId)

        // Check the latest capacity and entrant count from Firebase.
        firestore.runTransaction { transaction ->
            val document = transaction.get(lotteryRef)

            require(document.exists()) {
                "Lottery not found."
            }

            val currentCount = document.getLong("entrantCount")
                ?: throw IllegalStateException("Entrant count is missing.")

            val oldCapacity = document.getLong("waitlistCapacity")?.toInt()

            val registrationStart = document.getLong("registrationStart")
                ?: throw IllegalStateException("Registration start is missing.")

            val registrationStarted =
                System.currentTimeMillis() >= registrationStart

            // Check if the new capacity is allowed.
            if (newCapacity != oldCapacity) {
                LotteryCapacityValidator.validateCapacityUpdate(
                    oldCapacity = oldCapacity,
                    newCapacity = newCapacity,
                    registrationStarted = registrationStarted,
                    entrantCount = currentCount
                )
            }

            // Save the new capacity.
            transaction.update(lotteryRef, "waitlistCapacity", newCapacity)

            true
        }
            .addOnSuccessListener {
                // Update the local value after Firebase succeeds.
                lottery.waitlistCapacity = newCapacity
            }
            .addOnFailureListener { exception ->
                android.util.Log.e(
                    "LotteryModel",
                    "Failed to update waitlist capacity",
                    exception
                )
            }
    }

    // AI citation. OpenAI Codex, US 02.04.06 lottery edit form, navigation and Firebase write, 2026-11-27 (Edmonton).
    /** Saves listing settings after checking the current owner and registration cutoff. */
    fun updateLottery(
        lotteryId: String,
        ownerId: String,
        campsiteId: String,
        lotteryName: String,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int,
        waitlistCapacity: Int?,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val validator = LotteryValidator()
        validator.validateCampsiteId(campsiteId)
        val name = lotteryName.trim()
        require(name.isNotEmpty() && name.length <= 200) {
            "Enter a lottery name of 1 to 200 characters."
        }

        val firestore = FirebaseFirestore.getInstance()
        val collection = firestore.collection("lottery")
        val lotteryRef = collection.document(lotteryId)
        val campsites = firestore.collection("campsite")
        val settings = mapOf(
            "campsiteId" to campsiteId,
            "registrationStart" to registrationStart,
            "registrationEnd" to registrationEnd,
            "bookingStart" to bookingStart,
            "bookingEnd" to bookingEnd,
            "numberOfWinners" to numberOfWinners.toLong(),
            "waitlistCapacity" to waitlistCapacity?.toLong()
        )

        collection.whereEqualTo("campsiteId", campsiteId).get(Source.SERVER)
            .continueWithTask { query ->
                val others = query.result.documents.filter { it.id != lotteryId }
                firestore.runTransaction { transaction ->
                    val saved = transaction.get(lotteryRef)
                    val savedCampsiteId = saved.getString("campsiteId")
                        ?: throw IllegalArgumentException("Lottery or campsite not found.")
                    val savedCampsite = transaction.get(campsites.document(savedCampsiteId))
                    require(ownerId.isNotBlank() && savedCampsite.getString("ownerID") == ownerId) {
                        "Only the campsite owner can edit this lottery."
                    }

                    val changed = settings.filter { (field, value) ->
                        val current = if (field == "numberOfWinners") {
                            saved.getLong(field) ?: 1L
                        } else {
                            saved.get(field)
                        }
                        current != value
                    }
                    val updates = mutableMapOf<String, Any?>("lotteryName" to name)
                    if (changed.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val savedStart = saved.getLong("registrationStart")
                            ?: throw IllegalStateException("Registration start is missing.")
                        require(now < savedStart &&
                            saved.getBoolean("drawCompleted") != true &&
                            (saved.getString("drawStatus") ?: "PENDING") == "PENDING"
                        ) {
                            "Lottery settings are locked after registration begins. Reopen the form to edit the name."
                        }
                        if (campsiteId != savedCampsiteId) {
                            val selected = transaction.get(campsites.document(campsiteId))
                            require(selected.getString("ownerID") == ownerId) {
                                "Select a campsite that you own."
                            }
                        }
                        validator.validateFutureRegistrationStart(registrationStart, now)
                        validator.validateRegistrationPeriod(registrationStart, registrationEnd)
                        validator.validateRegistrationBeforeBooking(registrationEnd, bookingStart)
                        validator.validateBookingPeriod(bookingStart, bookingEnd)
                        validator.validateNumberOfWinners(numberOfWinners)
                        if ("waitlistCapacity" in changed) {
                            LotteryCapacityValidator.validateCapacityUpdate(
                                oldCapacity = saved.getLong("waitlistCapacity")?.toInt(),
                                newCapacity = waitlistCapacity,
                                registrationStarted = false,
                                entrantCount = saved.getLong("entrantCount") ?: 0L
                            )
                        }

                        if ("campsiteId" in changed || "bookingStart" in changed || "bookingEnd" in changed) {
                            others.forEach { match ->
                                val other = transaction.get(match.reference)
                                val otherStart = other.getLong("bookingStart")
                                val otherEnd = other.getLong("bookingEnd")
                                if (other.getString("campsiteId") == campsiteId &&
                                    otherStart != null && otherEnd != null
                                ) {
                                    validator.validateNoBookingOverlap(
                                        bookingStart, bookingEnd, otherStart, otherEnd
                                    )
                                }
                            }
                        }
                        updates.putAll(changed)
                    }
                    transaction.update(lotteryRef, updates)
                    Unit
                }
            }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    init {
        connector.initializeListener(
            localList = lotteries,
            type = Lottery::class.java,
            onError = { error ->
                android.util.Log.e(
                    "LotteryModel",
                    "Failed to load lotteries",
                    error
                )
            },
            onSuccess = {
                android.util.Log.d(
                    "LotteryModel",
                    "Lotteries loaded successfully"
                )
            }
        )
    }
}
