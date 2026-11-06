package com.example.zephyrcampsites

/**
 * Represents an entrant registered on a lottery waiting list
 * @param id Unique Document Id for this registration
 */
data class WaitingUser(
    override val id: String = ""
) : DataHolder(id){
    /** Lottery this entrant has joined. */
    var lotteryId: String = ""

    /** Device identified account belonging to entrant */
    var accountId : String = ""

    /** Time the entrant joined, in ms since unix epoch */
    var joinedAt : Long = 0L
}