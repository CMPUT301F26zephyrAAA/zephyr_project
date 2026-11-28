// Creates, edits, and listens to campsite lotteries in Firestore.
// Outstanding: the Android SDK cannot query inside a transaction, so the booking overlap
// recheck can miss a lottery for the same campsite created between the query and the commit.
package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import java.util.UUID

// AI Citation [AI-40]
// Anthropic, Claude Code assisted with making the Firestore
// instance injectable for emulator tests and stopping the listener.
// AI usage and citation documented in the team AI Documentation.
/**
 * Manages campsite lotteries and connects them to Firebase.
 *
 * @param firestore Database used for lotteries. Tests pass a local emulator instance.
 * @property lotteries Local copy of the lottery collection, updated by a snapshot listener.
 */
class LotteryModel(
    private val firestore: FirebaseFirestore = Firebase.firestore
) : ObservableModel<LotteryModel>() {

    val lotteries = mutableStateListOf<Lottery>()

    private val connector = FirebaseConnector("lottery", firestore)

    private var lotteryListener: ListenerRegistration? = null

    /**
     * Creates a lottery for a campsite.
     *
     * Dates are epoch milliseconds. The booking overlap is checked against the local list
     * first, then rechecked against the saved lotteries for the campsite in the save transaction.
     *
     * @param lotteryName Name shown for the lottery.
     * @param campsiteId ID of the campsite in the lottery.
     * @param registrationStart Start of registration.
     * @param registrationEnd End of registration.
     * @param bookingStart Start of the booking period.
     * @param bookingEnd End of the booking period.
     * @param numberOfWinners Number of winners to draw.
     * @param waitlistCapacity Maximum entrants, or null for no limit.
     * @param onSuccess Called with the lottery ID after Firebase saves it.
     * @param onFailure Called if the save fails, including an overlap found in the transaction.
     * @return ID of the new lottery.
     * @throws IllegalArgumentException If the input is invalid or overlaps a known lottery.
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

        val lotteryRef = firestore.collection("lottery").document(lottery.id)

        // AI Citation [AI-40]
        // Anthropic, Claude Code assisted with saving the new lottery
        // in a transaction that rechecks saved booking periods.
        // AI usage and citation documented in the team AI Documentation.
        // Recheck the saved booking periods while saving the lottery.
        loadOtherCampsiteLotteries(
            campsiteId = campsiteId,
            excludedLotteryId = lottery.id,
            onSuccess = { otherLotteries ->
                firestore.runTransaction { transaction ->
                    requireNoSavedBookingOverlap(
                        transaction,
                        otherLotteries,
                        bookingStart,
                        bookingEnd
                    )
                    transaction.set(lotteryRef, lottery)
                    true
                }
                    .addOnSuccessListener {
                        onSuccess(lottery.id)
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            },
            onFailure = onFailure
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
        firestore
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
     * Updates the booking period before registration starts.
     *
     * No screen calls this. The Edit Lottery screen uses [updateLottery].
     *
     * @param lotteryId ID of the lottery to update.
     * @param newStart New booking start in epoch milliseconds.
     * @param newEnd New booking end in epoch milliseconds.
     * @throws IllegalArgumentException If the lottery is missing, registration has started,
     * or the new period is invalid or overlaps a known lottery for the campsite.
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

        firestore
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
        firestore
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

    /**
     * Updates a lottery's settings from the Edit Lottery screen.
     *
     * Dates are epoch milliseconds. Dates are locked once registration starts. The lock and
     * the booking overlap are rechecked against saved data in the save transaction.
     *
     * @param lotteryId ID of the lottery to update.
     * @param lotteryName New lottery name.
     * @param registrationStart Registration start.
     * @param registrationEnd Registration end.
     * @param bookingStart Booking period start.
     * @param bookingEnd Booking period end.
     * @param numberOfWinners Number of winners to draw.
     * @param waitlistCapacity Maximum entrants, or null for no limit.
     * @param onSuccess Called after Firebase saves the changes.
     * @param onFailure Called if the save fails, including a lock or overlap found in the transaction.
     * @throws IllegalArgumentException If the input is invalid, dates changed after registration
     * started, or the booking period overlaps a known lottery for the campsite.
     */
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


        // Save lottery settings using the latest data from Firebase.
        val lotteryRef = firestore.collection("lottery").document(lotteryId)

        loadOtherCampsiteLotteries(
            campsiteId = lottery.campsiteId,
            excludedLotteryId = lotteryId,
            onSuccess = { otherLotteries ->
                saveLotteryUpdate(
                    lotteryRef,
                    otherLotteries,
                    lotteryName,
                    registrationStart,
                    registrationEnd,
                    bookingStart,
                    bookingEnd,
                    numberOfWinners,
                    waitlistCapacity,
                    onSuccess,
                    onFailure
                )
            },
            onFailure = onFailure
        )
    }

    // AI Citation [AI-40]
    // Anthropic, Claude Code assisted with moving the update transaction
    // into this function and adding the booking overlap recheck.
    // AI usage and citation documented in the team AI Documentation.
    /**
     * Saves validated lottery settings in a transaction against the latest saved data.
     *
     * @param lotteryRef Lottery document to update.
     * @param otherLotteries Other lotteries for the same campsite, from [loadOtherCampsiteLotteries].
     * @param lotteryName New lottery name.
     * @param registrationStart Registration start.
     * @param registrationEnd Registration end.
     * @param bookingStart Booking period start.
     * @param bookingEnd Booking period end.
     * @param numberOfWinners Number of winners to draw.
     * @param waitlistCapacity Maximum entrants, or null for no limit.
     * @param onSuccess Called after Firebase saves the changes.
     * @param onFailure Called if the transaction fails.
     */
    private fun saveLotteryUpdate(
        lotteryRef: DocumentReference,
        otherLotteries: List<DocumentReference>,
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

            // Recheck the saved booking periods for this campsite.
            requireNoSavedBookingOverlap(
                transaction,
                otherLotteries,
                bookingStart,
                bookingEnd
            )

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

    // AI Citation [AI-40]
    // Anthropic, Claude Code assisted with loading the campsite's other
    // lotteries and rechecking their booking periods inside a transaction.
    // AI usage and citation documented in the team AI Documentation.
    /**
     * Finds the other saved lotteries for a campsite so a transaction can re-read them.
     *
     * Android transactions cannot run queries, so this query runs just before the transaction.
     *
     * @param campsiteId Campsite whose lotteries are loaded.
     * @param excludedLotteryId Lottery being saved, left out of the result.
     * @param onSuccess Called with references to the other lotteries.
     * @param onFailure Called if the query fails.
     */
    private fun loadOtherCampsiteLotteries(
        campsiteId: String,
        excludedLotteryId: String,
        onSuccess: (List<DocumentReference>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        firestore.collection("lottery")
            .whereEqualTo("campsiteId", campsiteId)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents
                        .filter { it.id != excludedLotteryId }
                        .map { it.reference }
                )
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Rereads other lotteries in a transaction and rejects an overlapping booking period.
     *
     * @param transaction Transaction saving the lottery. Must run before any write in it.
     * @param otherLotteries Other lotteries for the same campsite.
     * @param bookingStart Booking period start being saved.
     * @param bookingEnd Booking period end being saved.
     * @throws IllegalArgumentException If a saved booking period overlaps.
     */
    private fun requireNoSavedBookingOverlap(
        transaction: Transaction,
        otherLotteries: List<DocumentReference>,
        bookingStart: Long,
        bookingEnd: Long
    ) {
        otherLotteries.forEach { reference ->
            val other = transaction.get(reference)
            val otherStart = other.getLong("bookingStart")
            val otherEnd = other.getLong("bookingEnd")

            // A deleted lottery or an older record without booking dates does not block these dates.
            if (other.exists() && otherStart != null && otherEnd != null) {
                LotteryValidator().validateNoBookingOverlap(
                    bookingStart,
                    bookingEnd,
                    otherStart,
                    otherEnd
                )
            }
        }
    }

    /**
     * Stops the lottery snapshot listener.
     */
    fun stopListening() {
        lotteryListener?.remove()
        lotteryListener = null
    }

    init {
        lotteryListener = connector.initializeListener(
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
