
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
        campsiteId: String,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int = 1,
        waitlistCapacity: Int? = null
    ) {
        require(campsiteId.isNotBlank()) {
            "A campsite must be selected."
        }
        require(registrationStart < registrationEnd) {
            "Invalid registration period."
        }
        require(bookingStart < bookingEnd) {
            "Invalid booking period."
        }

        // Booking must start after registration ends.
        require(bookingStart > registrationEnd) {
            "Booking must start after registration ends."
        }

        // Check for overlapping booking periods for the same campsite.
        val hasOverlap = lotteries.any { other ->
            other.campsiteId == campsiteId &&
                    bookingStart < other.bookingEnd &&
                    bookingEnd > other.bookingStart
        }

        require(!hasOverlap) {
            "Booking period overlaps with another lottery for this campsite."
        }

        require(numberOfWinners > 0) {
            "Number of winners must be positive."
        }
        require(waitlistCapacity == null || waitlistCapacity > 0) {
            "Waitlist capacity must be positive."
        }

        require(!hasOverlap) {
            "This campsite already has a lottery with an overlapping booking period."
        }

        val lottery = Lottery(UUID.randomUUID().toString())

        lottery.campsiteId = campsiteId
        lottery.registrationStart = registrationStart
        lottery.registrationEnd = registrationEnd
        lottery.bookingStart = bookingStart
        lottery.bookingEnd = bookingEnd
        lottery.numberOfWinners = numberOfWinners
        lottery.waitlistCapacity = waitlistCapacity

        connector.add(lottery)
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

        // Registration dates cannot be changed once registration begins.
        require(System.currentTimeMillis() < lottery.registrationStart) {
            "Registration has already started. Dates cannot be changed."
        }

        // Make sure the new registration period is valid and in the future.
        require(newStart > System.currentTimeMillis() && newStart < newEnd) {
            "Enter a valid future registration period."
        }

        // Update the registration dates.
        lottery.registrationStart = newStart
        lottery.registrationEnd = newEnd

        // Save the updated registration dates to Firebase.
        connector.add(lottery)
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
        require(System.currentTimeMillis() < lottery.registrationStart) {
            "Registration has already started. Booking dates cannot be changed."
        }

        // Check the new booking dates.
        require(newStart > lottery.registrationEnd) {
            "Booking must start after registration ends."
        }

        require(newStart < newEnd) {
            "Booking end must be after booking start."
        }

        // Check if another lottery for this campsite has overlapping dates.
        val hasOverlap = lotteries.any { other ->
            other.id != lotteryId &&
                    other.campsiteId == lottery.campsiteId &&
                    newStart <= other.bookingEnd &&
                    newEnd >= other.bookingStart
        }

        require(!hasOverlap) {
            "Booking period overlaps with another lottery for this campsite."
        }

        require(newStart > System.currentTimeMillis()) {
            "Booking start must be in the future."
        }

        lottery.bookingStart = newStart
        lottery.bookingEnd = newEnd

        connector.add(lottery)
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
