
package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import java.util.UUID

// AI Citation [AI-42]
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
     */
    fun createLottery(
        lotteryName: String,
        campsiteIds: List<String>,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int = 1,
        waitlistCapacity: Int?,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ): String {
        require(campsiteIds.isNotEmpty()) { "Select at least one campsite." }

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

        require(numberOfWinners > 0) {
            "Number of winners must be positive."
        }
        require(waitlistCapacity == null || waitlistCapacity > 0) {
            "Waitlist capacity must be positive."
        }

        // Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
        val lotteryId = UUID.randomUUID().toString()
        functions.getHttpsCallable("saveLotterySettings")
            .call(
                mapOf(
                    "create" to true,
                    "lotteryId" to lotteryId,
                    "campsiteIds" to campsiteIds,
                    "lotteryName" to lotteryName,
                    "registrationStart" to registrationStart,
                    "registrationEnd" to registrationEnd,
                    "bookingStart" to bookingStart,
                    "bookingEnd" to bookingEnd,
                    "numberOfWinners" to numberOfWinners,
                    "waitlistCapacity" to waitlistCapacity
                )
            )
            .addOnSuccessListener { onSuccess(lotteryId) }
            .addOnFailureListener { onFailure(it) }
        return lotteryId
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
                        other.participatingCampsiteIds().any { it in lottery.participatingCampsiteIds() }
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


        functions.getHttpsCallable("saveLotterySettings")
            .call(
                mapOf(
                    "lotteryId" to lotteryId,
                    "lotteryName" to lotteryName,
                    "registrationStart" to registrationStart,
                    "registrationEnd" to registrationEnd,
                    "bookingStart" to bookingStart,
                    "bookingEnd" to bookingEnd,
                    "numberOfWinners" to numberOfWinners,
                    "waitlistCapacity" to waitlistCapacity
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
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
