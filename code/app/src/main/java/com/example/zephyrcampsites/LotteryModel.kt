
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

    fun updateLottery(
        lotteryId: String,
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
        // Find the lottery that needs to be updated.
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")

        val now = System.currentTimeMillis()

        // Check the lottery name.
        require(lotteryName.isNotBlank()) {
            "Lottery name cannot be empty."
        }

        // Registration dates cannot be changed after registration starts.
        require(
            now < lottery.registrationStart ||
                    (registrationStart == lottery.registrationStart &&
                            registrationEnd == lottery.registrationEnd)
        ) {
            "Registration period cannot be changed after registration starts."
        }

        // Booking dates cannot be changed after registration starts.
        require(
            now < lottery.registrationStart ||
                    (bookingStart == lottery.bookingStart &&
                            bookingEnd == lottery.bookingEnd)
        ) {
            "Booking period cannot be changed after registration starts."
        }

        // Check that the registration period is valid.
        require(registrationStart < registrationEnd) {
            "Invalid registration period."
        }

        // A changed registration start date must be in the future.
        if (registrationStart != lottery.registrationStart) {
            require(registrationStart > now) {
                "Registration start must be in the future."
            }
        }

        // Registration must finish before booking begins.
        LotteryValidator().validateRegistrationBeforeBooking(
            registrationEnd,
            bookingStart
        )

        require(registrationEnd < bookingStart && bookingStart < bookingEnd) {
            "Booking must start after registration ends and end after it starts."
        }

        // Check that a newly changed booking start is in the future.
        if (bookingStart != lottery.bookingStart) {
            require(bookingStart > System.currentTimeMillis()) {
                "Booking start must be in the future."
            }
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

        // Winner count can only be changed before registration starts.
        if (numberOfWinners != lottery.numberOfWinners) {
            LotteryValidator().validateRegistrationNotStarted(
                lottery.registrationStart,
                now
            )
        }

        // Check that the winner count is valid.
        LotteryValidator().validateNumberOfWinners(numberOfWinners)

        // Check whether the waitlist capacity can be changed.
        if (waitlistCapacity != lottery.waitlistCapacity) {
            val registrationStarted = now >= lottery.registrationStart

            LotteryCapacityValidator.validateCapacityUpdate(
                oldCapacity = lottery.waitlistCapacity,
                newCapacity = waitlistCapacity,
                registrationStarted = registrationStarted,
                entrantCount = lottery.entrantCount
            )
        }


        // Save lottery settings using the latest data from Firebase.
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val lotteryRef = firestore.collection("lottery").document(lotteryId)

        firestore.runTransaction { transaction ->
            val document = transaction.get(lotteryRef)

            require(document.exists()) {
                "Lottery not found."
            }

            val currentCount = document.getLong("entrantCount")
                ?: throw IllegalStateException("Entrant count is missing.")
            // Check the latest registration dates before saving.
            val savedRegistrationStart = document.getLong("registrationStart")
                ?: throw IllegalStateException("Registration start is missing.")

            val savedRegistrationEnd = document.getLong("registrationEnd")
                ?: throw IllegalStateException("Registration end is missing.")

            val savedBookingStart = document.getLong("bookingStart")
                ?: throw IllegalStateException("Booking start is missing.")

            val savedBookingEnd = document.getLong("bookingEnd")
                ?: throw IllegalStateException("Booking end is missing.")

            if (System.currentTimeMillis() >= savedRegistrationStart) {
                require(
                    registrationStart == savedRegistrationStart &&
                            registrationEnd == savedRegistrationEnd &&
                            bookingStart == savedBookingStart &&
                            bookingEnd == savedBookingEnd
                ) {
                    "Registration and booking dates cannot be changed after registration starts."
                }
            }

            val oldCapacity = document.getLong("waitlistCapacity")?.toInt()
            val registrationStarted =
                System.currentTimeMillis() >=
                        (document.getLong("registrationStart")
                            ?: throw IllegalStateException("Registration start is missing."))

            if (waitlistCapacity != oldCapacity) {
                LotteryCapacityValidator.validateCapacityUpdate(
                    oldCapacity = oldCapacity,
                    newCapacity = waitlistCapacity,
                    registrationStarted = registrationStarted,
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
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
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
