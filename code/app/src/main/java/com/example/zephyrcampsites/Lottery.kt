package com.example.zephyrcampsites

// AI Citation [AI-40]
// Anthropic, Claude Code assisted with documenting the
// lottery fields and why dates stay in epoch milliseconds.
// AI usage and citation documented in the team AI Documentation.
/**
 * Represents a lottery listing for a campsite.
 *
 * Each lottery belongs to one campsite and has
 * its own registration and booking settings.
 *
 * Dates are stored as epoch milliseconds (UTC), not Firestore Timestamps. Firestore rules,
 * the lottery draw Cloud Function, and existing lottery documents all compare these fields
 * as numbers, so changing the type needs a coordinated migration.
 *
 * @property lotteryName Name shown for the lottery.
 * @property campsiteId ID of the campsite in the lottery.
 * @property registrationStart Registration start in epoch milliseconds.
 * @property registrationEnd Registration end in epoch milliseconds.
 * @property bookingStart Booking period start in epoch milliseconds.
 * @property bookingEnd Booking period end in epoch milliseconds.
 * @property numberOfWinners Number of winners to draw.
 * @property waitlistCapacity Maximum entrants, or null for no limit.
 * @property entrantCount Number of entrants on the waitlist.
 */
data class Lottery(
    override val id: String = ""
) : DataHolder(id) {

    var lotteryName: String = ""
    var campsiteId: String = ""
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
