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
}
