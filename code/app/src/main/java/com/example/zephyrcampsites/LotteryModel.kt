// Persists lottery settings and observes lottery records in Firestore.

package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.functions.FirebaseFunctions
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
        FirebaseFunctions.getInstance().getHttpsCallable("saveLotterySettings")
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
        campsiteIds: List<String>,
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
        require(campsiteIds.isNotEmpty()) { "Select at least one campsite." }
        FirebaseFunctions.getInstance().getHttpsCallable("saveLotterySettings")
            .call(
                mapOf(
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
