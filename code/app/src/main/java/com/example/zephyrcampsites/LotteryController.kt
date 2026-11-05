package com.example.zephyrcampsites
/**
 * Handles actions related to campsite lotteries.
 */
class LotteryController(
    private val lotteryModel: LotteryModel
) {

    fun createLottery(
        campsiteId: String,
        registrationStart: Long,
        registrationEnd: Long,
        bookingStart: Long,
        bookingEnd: Long,
        numberOfWinners: Int,
        waitlistCapacity: Int?
    ) {
        lotteryModel.createLottery(
            campsiteId = campsiteId,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = numberOfWinners,
            waitlistCapacity = waitlistCapacity
        )
    }

    /**
     * Updates the registration period for an existing lottery.
     *
     * @param lotteryId ID of the lottery to update.package com.example.zephyrcampsites
     * /**
     *  * Handles actions related to campsite lotteries.
     *  */
     * class LotteryController(
     *     private val lotteryModel: LotteryModel
     * ) {
     *
     *     fun createLottery(
     *         campsiteId: String,
     *         registrationStart: Long,
     *         registrationEnd: Long,
     *         bookingStart: Long,
     *         bookingEnd: Long,
     *         numberOfWinners: Int,
     *         waitlistCapacity: Int?
     *     ) {
     *         lotteryModel.createLottery(
     *             campsiteId = campsiteId,
     *             registrationStart = registrationStart,
     *             registrationEnd = registrationEnd,
     *             bookingStart = bookingStart,
     *             bookingEnd = bookingEnd,
     *             numberOfWinners = numberOfWinners,
     *             waitlistCapacity = waitlistCapacity
     *         )
     *     }
     *
     *     /**
     *      * Updates the registration period for an existing lottery.
     *      *
     *      * @param lotteryId ID of the lottery to update.
     *      * @param newStart New registration start date and time.
     *      * @param newEnd New registration end date and time.
     *      */
     *     fun updateRegistrationPeriod(
     *         lotteryId: String,
     *         newStart: Long,
     *         newEnd: Long
     *     ) {
     *         lotteryModel.updateRegistrationPeriod(
     *             lotteryId,
     *             newStart,
     *             newEnd
     *         )
     *     }
     *
     *     /**
     *      * Updates the booking period for an existing lottery.
     *      *
     *      * @param lotteryId ID of the lottery to update.
     *      * @param newStart New booking start date and time.
     *      * @param newEnd New booking end date and time.
     *      */
     *     fun updateBookingPeriod(
     *         lotteryId: String,
     *         newStart: Long,
     *         newEnd: Long
     *     ) {
     *         lotteryModel.updateBookingPeriod(
     *             lotteryId,
     *             newStart,
     *             newEnd
     *         )
     *     }
     *
     *     /**
     *      * Finds the lottery associated with a campsite.
     *      *
     *      * @param campsiteId ID of the selected campsite.
     *      * @return The matching lottery, or null if none exists.
     *      */
     *     fun getLotteryByCampsiteId(campsiteId: String): Lottery? {
     *         return lotteryModel.lotteries.find {
     *             it.campsiteId == campsiteId
     *         }
     *     }
     * }
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
     */
    fun updateNumberOfWinners(lotteryId: String, newCount: Int) {
        lotteryModel.updateNumberOfWinners(lotteryId, newCount)
    }

    /**
     * Updates the waitlist capacity for a lottery.
     */
    fun updateWaitlistCapacity(lotteryId: String, newCapacity: Int?) {
        lotteryModel.updateWaitlistCapacity(lotteryId, newCapacity)
    }
}
