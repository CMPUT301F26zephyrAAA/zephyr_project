package com.example.zephyrcampsites
/**
 * Handles actions related to campsite lotteries.
 */
class LotteryController(
    private val lotteryModel: LotteryModel
) {

    fun createLottery(
        lotteryName: String,
        campsiteIds: List<String>, // Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
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
            campsiteIds = campsiteIds,
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
     * Finds the lottery associated with a campsite.
     *
     * @param campsiteId ID of the selected campsite.
     * @return The matching lottery, or null if none exists.
     */
    fun getLotteryByCampsiteId(campsiteId: String): Lottery? {
        return lotteryModel.lotteries.find {
            campsiteId in it.participatingCampsiteIds()
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

    /**
    Find a lottery using its ID.*/
    fun getLotteryById(lotteryId: String): Lottery? {
        return lotteryModel.lotteries.find {
            it.id == lotteryId
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

