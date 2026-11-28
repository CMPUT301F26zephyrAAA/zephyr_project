package com.example.zephyrcampsites
/**
 * Represents a lottery listing for a campsite.
 *
 * Each lottery links one or more campsites and has
 * its own registration and booking settings.
 */
data class Lottery(
    override val id: String = ""
) : DataHolder(id) {

    var lotteryName: String = ""
    var campsiteId: String = ""
    var campsiteIds: List<String> = emptyList()

    /** Returns all linked sites, including the original site for older records. */
    fun participatingCampsiteIds(): List<String> = campsiteIds.ifEmpty {
        listOf(campsiteId).filter { it.isNotBlank() }
    }
    var registrationStart: Long = 0L
    var registrationEnd: Long = 0L
    var bookingStart: Long = 0L
    var bookingEnd: Long = 0L
    var numberOfWinners: Int = 1
    var waitlistCapacity: Int? = null
    var entrantCount: Long = 0L

    // Tracks whether the lottery winner draw has already finished.
    var drawCompleted: Boolean = false

    // Stores when the winner draw was completed.
    var drawCompletedAt: Long = 0L

    // Tracks whether the lottery draw is waiting, running, or finished.
    var drawStatus: String = "PENDING"

    // Time when the current lottery draw started.
    var drawStartedAt: Long = 0L

}
