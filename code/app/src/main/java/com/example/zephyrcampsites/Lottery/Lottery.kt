package com.example.zephyrcampsites.Lottery

import com.example.zephyrcampsites.DataHolder
import java.util.Date

/**
 * Data class for a lottery.
 *
 * No methods, only data holder. ID is given to create, and includes var to allow owner to modify fields.
 *
 * @param lotteryId the lottery id to assign to this instance.
 */
data class Lottery(val lotteryId: String = "0") : DataHolder(lotteryId) {  // do we import the id from Data Holder class here too
    var campsiteIDList: MutableList<String> = mutableListOf()
    var registrationPeriodStart: Date = Date(1)
    var registrationPeriodEnd: Date = Date(1793723395)
    var lotteryWinnersCapacity: Int = 0
    var waitlistCapacity: Int = 0
}