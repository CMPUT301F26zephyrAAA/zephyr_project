
package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
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
        // Check that the winner count is positive.
        LotteryValidator().validateNumberOfWinners(newCount)

        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val lotteryRef = firestore.collection("lottery").document(lotteryId)

        // Read the latest registration date before updating the winner count.
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(lotteryRef)

            require(snapshot.exists()) {
                "Lottery not found."
            }

            val registrationStart = snapshot.getLong("registrationStart")
                ?: throw IllegalArgumentException("Registration start date not found.")

            // Winner count cannot be changed after registration starts.
            LotteryValidator().validateRegistrationNotStarted(
                registrationStart,
                System.currentTimeMillis()
            )

            transaction.update(lotteryRef, "numberOfWinners", newCount)
            null
        }.addOnSuccessListener {
            // Update the local lottery after Firebase saves the change.
            lotteries.find { it.id == lotteryId }?.numberOfWinners = newCount
        }.addOnFailureListener { exception ->
            android.util.Log.e(
                "LotteryModel",
                "Failed to update number of winners",
                exception
            )
        }
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

    fun updateLottery(
        lotteryId: String,
        lotteryName: String,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int,
        waitlistCapacity: Int?,
        originalLottery: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Find the lottery that needs to be updated.
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")


        // Check the lottery name.
        require(lotteryName.isNotBlank()) {
            "Lottery name cannot be empty."
        }

        // Check that the registration period is valid.
        require(registrationStart < registrationEnd) {
            "Invalid registration period."
        }

        // Registration must finish before booking begins.
        LotteryValidator().validateRegistrationBeforeBooking(
            registrationEnd,
            bookingStart
        )

        require(registrationEnd < bookingStart && bookingStart < bookingEnd) {
            "Booking must start after registration ends and end after it starts."
        }


        // Check for overlapping booking periods on the same campsite.
        lotteries
            .filter { other ->
                other.id != lotteryId &&
                        other.campsiteId == lottery.campsiteId
            }
            .forEach { other ->
                LotteryValidator().validateNoBookingOverlap(
                    bookingStart,
                    bookingEnd,
                    other.bookingStart,
                    other.bookingEnd
                )
            }


        // Check that the winner count is valid.
        LotteryValidator().validateNumberOfWinners(numberOfWinners)


        // Update the lottery settings using the latest Firebase data.
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val lotteryRef = firestore.collection("lottery").document(lotteryId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(lotteryRef)

            require(snapshot.exists()) {
                "Lottery not found."
            }

            val currentCount = snapshot.getLong("entrantCount")
                ?: throw IllegalStateException("Entrant count is missing.")

            val savedRegistrationStart = snapshot.getLong("registrationStart")
                ?: throw IllegalArgumentException("Registration start date not found.")

            val savedRegistrationEnd = snapshot.getLong("registrationEnd")
                ?: throw IllegalArgumentException("Registration end date not found.")

            val savedBookingStart = snapshot.getLong("bookingStart")
                ?: throw IllegalArgumentException("Booking start date not found.")

            val savedBookingEnd = snapshot.getLong("bookingEnd")
                ?: throw IllegalArgumentException("Booking end date not found.")

            val savedWinnerCount = snapshot.getLong("numberOfWinners")
                ?: throw IllegalArgumentException("Winner count not found.")

            // A null capacity means the lottery has unlimited capacity.
            val savedCapacity = snapshot.getLong("waitlistCapacity")?.toInt()

            // Check whether the settings changed while the owner was editing.
            val savedSettings = mapOf<String, Any?>(
                "lotteryName" to snapshot.getString("lotteryName"),
                "registrationStart" to savedRegistrationStart,
                "registrationEnd" to savedRegistrationEnd,
                "bookingStart" to savedBookingStart,
                "bookingEnd" to savedBookingEnd,
                "numberOfWinners" to savedWinnerCount.toInt(),
                "waitlistCapacity" to savedCapacity
            )

            // Do not overwrite newer changes made in Firebase.
            require(savedSettings == originalLottery) {
                "Lottery settings have changed. Please reopen the lottery and try again."
            }

            val currentTime = System.currentTimeMillis()

            // Registration dates cannot be changed after registration starts.
            require(
                currentTime < savedRegistrationStart ||
                        (registrationStart == savedRegistrationStart &&
                                registrationEnd == savedRegistrationEnd)
            ) {
                "Registration period cannot be changed after registration starts."
            }

            // Booking dates cannot be changed after registration starts.
            require(
                currentTime < savedRegistrationStart ||
                        (bookingStart == savedBookingStart &&
                                bookingEnd == savedBookingEnd)
            ) {
                "Booking period cannot be changed after registration starts."
            }

            // Winner count cannot be changed after registration starts.
            if (numberOfWinners.toLong() != savedWinnerCount) {
                LotteryValidator().validateRegistrationNotStarted(
                    savedRegistrationStart,
                    currentTime
                )
            }

            // Check that the changed registration start is in the future.
            if (registrationStart != savedRegistrationStart) {
                LotteryValidator().validateFutureRegistrationStart(
                    registrationStart,
                    currentTime
                )
            }

            // Check that the changed booking start is in the future.
            if (bookingStart != savedBookingStart) {
                require(bookingStart > currentTime) {
                    "Booking start must be in the future."
                }
            }

            // Validate capacity using the latest capacity and entrant count.
            if (waitlistCapacity != savedCapacity) {
                LotteryCapacityValidator.validateCapacityUpdate(
                    oldCapacity = savedCapacity,
                    newCapacity = waitlistCapacity,
                    registrationStarted = currentTime >= savedRegistrationStart,
                    entrantCount = currentCount
                )
            }

            // Save settings without overwriting the entrant count.
            transaction.update(
                lotteryRef,
                mapOf(
                    "lotteryName" to lotteryName.trim(),
                    "registrationStart" to registrationStart,
                    "registrationEnd" to registrationEnd,
                    "bookingStart" to bookingStart,
                    "bookingEnd" to bookingEnd,
                    "numberOfWinners" to numberOfWinners,
                    "waitlistCapacity" to waitlistCapacity
                )
            )

            true
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { exception ->
            onFailure(exception)
        }

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
