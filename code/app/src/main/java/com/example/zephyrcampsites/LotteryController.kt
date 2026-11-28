package com.example.zephyrcampsites

// AI Citation [AI-40]
// Anthropic, Claude Code assisted with KDoc for the
// lottery create, update, and lookup methods.
// AI usage and citation documented in the team AI Documentation.
/**
 * Handles actions related to campsite lotteries.
 */
class LotteryController(
    private val lotteryModel: LotteryModel
) {

    /**
     * Creates a lottery for a campsite.
     *
     * @param lotteryName Name shown for the lottery.
     * @param campsiteId ID of the campsite in the lottery.
     * @param registrationStart Registration start in epoch milliseconds.
     * @param registrationEnd Registration end in epoch milliseconds.
     * @param bookingStart Booking period start in epoch milliseconds.
     * @param bookingEnd Booking period end in epoch milliseconds.
     * @param numberOfWinners Number of winners to draw.
     * @param waitlistCapacity Maximum entrants, or null for no limit.
     * @param onSuccess Called with the lottery ID after Firebase saves it.
     * @param onFailure Called if the save fails.
     * @return ID of the new lottery.
     * @throws IllegalArgumentException If the input is invalid.
     */
    fun createLottery(
        lotteryName: String,
        campsiteId: String,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int,
        waitlistCapacity: Int?,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ): String {
        return lotteryModel.createLottery(
            lotteryName = lotteryName,
            campsiteId = campsiteId,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = numberOfWinners,
            waitlistCapacity = waitlistCapacity,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Updates the registration period for an existing lottery.
     *
     * @param lotteryId ID of the lottery to update.
     * @param newStart New registration start date and time.
     * @param newEnd New registration end date and time.
     */
    fun updateRegistrationPeriod(
        lotteryId: String,
        newStart: Long,
        newEnd: Long
    ) {
        lotteryModel.updateRegistrationPeriod(
            lotteryId,
            newStart,
            newEnd
        )
    }

    /**
     * Updates the booking period for an existing lottery.
     *
     * @param lotteryId ID of the lottery to update.
     * @param newStart New booking start date and time.
     * @param newEnd New booking end date and time.
     */
    fun updateBookingPeriod(
        lotteryId: String,
        newStart: Long,
        newEnd: Long
    ) {
        lotteryModel.updateBookingPeriod(
            lotteryId,
            newStart,
            newEnd
        )
    }

    /**
     * Finds the lottery associated with a campsite.
     *
     * @param campsiteId ID of the selected campsite.
     * @return The matching lottery, or null if none exists.
     */
    fun getLotteryByCampsiteId(campsiteId: String): Lottery? {
        return lotteryModel.lotteries.find {
            it.campsiteId == campsiteId
        }
    }

    /**
     * Updates the number of winners for a lottery.
     *
     * @param lotteryId ID of the lottery to update.
     * @param newCount New number of winners.
     */
    fun updateNumberOfWinners(lotteryId: String, newCount: Int) {
        lotteryModel.updateNumberOfWinners(lotteryId, newCount)
    }

    /**
     * Updates the waitlist capacity for a lottery.
     *
     * @param lotteryId ID of the lottery to update.
     * @param newCapacity New maximum entrants, or null for no limit.
     */
    fun updateWaitlistCapacity(lotteryId: String, newCapacity: Int?) {
        lotteryModel.updateWaitlistCapacity(lotteryId, newCapacity)
    }

    /**
     * Finds a lottery using its ID.
     *
     * @param lotteryId ID of the lottery.
     * @return The matching lottery, or null if none exists.
     */
    fun getLotteryById(lotteryId: String): Lottery? {
        return lotteryModel.lotteries.find {
            it.id == lotteryId
        }
    }

    /**
     * Updates a lottery's settings from the Edit Lottery screen.
     *
     * @param lotteryId ID of the lottery to update.
     * @param lotteryName New lottery name.
     * @param registrationStart Registration start in epoch milliseconds.
     * @param registrationEnd Registration end in epoch milliseconds.
     * @param bookingStart Booking period start in epoch milliseconds.
     * @param bookingEnd Booking period end in epoch milliseconds.
     * @param numberOfWinners Number of winners to draw.
     * @param waitlistCapacity Maximum entrants, or null for no limit.
     * @param onSuccess Called after Firebase saves the changes.
     * @param onFailure Called if the save fails.
     * @throws IllegalArgumentException If the input is invalid or dates changed after
     * registration started.
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
        lotteryModel.updateLottery(
            lotteryId = lotteryId,
            lotteryName = lotteryName,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = numberOfWinners,
            waitlistCapacity = waitlistCapacity,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}

