package com.example.zephyrcampsites
/**
 * Represents a lottery listing for a campsite.
 *
 * Each lottery belongs to one campsite and has
 * its own registration and booking settings.
 */
data class Lottery(
    override val id: String = ""
) : DataHolder(id) {

    var campsiteId: String = ""
    var registrationStart: Long = 0L
    var registrationEnd: Long = 0L
    var bookingStart: Long = 0L
    var bookingEnd: Long = 0L
    var numberOfWinners: Int = 1
    var waitlistCapacity: Int? = null
}
