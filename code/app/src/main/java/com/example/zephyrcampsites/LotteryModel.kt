// Creates, edits, and listens to campsite lotteries.
// Creating and editing go through the createLottery and updateLottery Cloud Functions,
// which recheck ownership, booking overlap, and the date cutoff with server time.
package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import java.util.UUID

// AI Citation [AI-40], [AI-41]
// Anthropic, Claude Code assisted with making Firestore and Functions
// injectable for emulator tests and stopping the listener.
// AI usage and citation documented in the team AI Documentation.
/**
 * Manages campsite lotteries and connects them to Firebase.
 *
 * @param firestore Database used for lotteries. Tests pass a local emulator instance.
 * @param functions Cloud Functions used to save lotteries. Tests pass a local emulator instance.
 * @property lotteries Local copy of the lottery collection, updated by a snapshot listener.
 */
class LotteryModel(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : ObservableModel<LotteryModel>() {

    val lotteries = mutableStateListOf<Lottery>()

    private val connector = FirebaseConnector("lottery", firestore)

    private var lotteryListener: ListenerRegistration? = null

    /**
     * Creates a lottery for a campsite.
     *
     * Dates are epoch milliseconds. The input is checked here for quick feedback, then the
     * createLottery Cloud Function rechecks it and the booking overlap with server time.
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
     * @param onFailure Called if the save fails, including a check rejected by the server.
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

        // The app picks the ID so the screen can open the lottery after it saves.
        val lotteryId = UUID.randomUUID().toString()

        // AI Citation [AI-41]
        // Anthropic, Claude Code assisted with saving new lotteries through
        // a Cloud Function that rechecks overlap with server time.
        // AI usage and citation documented in the team AI Documentation.
        callLotteryFunction(
            name = "createLottery",
            data = mapOf(
                "lotteryId" to lotteryId,
                "campsiteId" to campsiteId,
                "lotteryName" to lotteryName.trim(),
                "registrationStart" to registrationStart,
                "registrationEnd" to registrationEnd,
                "bookingStart" to bookingStart,
                "bookingEnd" to bookingEnd,
                "numberOfWinners" to numberOfWinners,
                "waitlistCapacity" to waitlistCapacity
            ),
            onSuccess = { onSuccess(lotteryId) },
            onFailure = onFailure
        )
        return lotteryId
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
     * Dates are epoch milliseconds and are locked once registration starts. The input is
     * checked here for quick feedback, then the updateLottery Cloud Function rechecks the
     * lock, capacity, and booking overlap against saved data using server time.
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
     * @param onFailure Called if the save fails, including a check rejected by the server.
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


        // AI Citation [AI-41]
        // Anthropic, Claude Code assisted with saving edits through a Cloud Function
        // that rechecks the date lock and overlap with server time.
        // AI usage and citation documented in the team AI Documentation.
        callLotteryFunction(
            name = "updateLottery",
            data = mapOf(
                "lotteryId" to lotteryId,
                "lotteryName" to lotteryName.trim(),
                "registrationStart" to registrationStart,
                "registrationEnd" to registrationEnd,
                "bookingStart" to bookingStart,
                "bookingEnd" to bookingEnd,
                "numberOfWinners" to numberOfWinners,
                "waitlistCapacity" to waitlistCapacity
            ),
            onSuccess = { onSuccess() },
            onFailure = onFailure
        )
    }

    /**
     * Calls a lottery Cloud Function and reports the result.
     *
     * @param name Callable function name.
     * @param data Lottery settings sent to the function.
     * @param onSuccess Called after the function saves the lottery.
     * @param onFailure Called with the function's error, including server-side check failures.
     */
    private fun callLotteryFunction(
        name: String,
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        functions.getHttpsCallable(name)
            .call(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onFailure(exception) }
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
