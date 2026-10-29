
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

    init {
        connector.initializeListener(
            lotteries,
            Lottery::class.java
        )
    }
}
