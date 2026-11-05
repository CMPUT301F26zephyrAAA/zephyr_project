
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
        LotteryValidator().validateCampsiteId(campsiteId)

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

        connector.add(lottery)
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
        connector.add(lottery)
    }
    /**
     * Updates the waitlist capacity for a lottery.
     */
    fun updateWaitlistCapacity(lotteryId: String, newCapacity: Int?) {
        val lottery = lotteries.find { it.id == lotteryId }
            ?: throw IllegalArgumentException("Lottery not found.")

        val registrationStarted =
            System.currentTimeMillis() >= lottery.registrationStart

        // Check if the capacity update is allowed.
        LotteryCapacityValidator.validateCapacityUpdate(
            oldCapacity = lottery.waitlistCapacity,
            newCapacity = newCapacity,
            registrationStarted = registrationStarted
        )

        lottery.waitlistCapacity = newCapacity

        // Save the updated capacity to Firebase.
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
