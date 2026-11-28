package com.example.zephyrcampsites

// AI Citation [AI-40]
// Anthropic, Claude Code assisted with KDoc for the
// booking period, overlap, and edit cutoff validators.
// AI usage and citation documented in the team AI Documentation.
/**
 * Validates the information used to create a lottery.
 */
class LotteryValidator {

    // Check that a campsite ID is provided.
    fun validateCampsiteId(campsiteId: String) {
        require(campsiteId.isNotBlank()) {
            "A campsite must be selected."
        }
    }
    /**
     * Checks that the registration period is valid.
     */
    fun validateRegistrationPeriod(start: Long, end: Long) {
        require(start < end) {
            "Invalid registration period."
        }
    }

    /**
     * Checks that the booking period is valid.
     *
     * A single-day period is valid when the end time is later than the start time.
     *
     * @param start Booking start in epoch milliseconds.
     * @param end Booking end in epoch milliseconds.
     * @throws IllegalArgumentException If the end is not after the start.
     */
    fun validateBookingPeriod(start: Long, end: Long) {
        require(start < end) {
            "Booking end must be after booking start."
        }
    }

    /**
     * Checks if two booking periods overlap.
     *
     * Periods that share an endpoint count as overlapping.
     *
     * @param newStart Start of the period being saved.
     * @param newEnd End of the period being saved.
     * @param existingStart Start of another lottery's period for the same campsite.
     * @param existingEnd End of another lottery's period for the same campsite.
     * @throws IllegalArgumentException If the periods overlap.
     */
    fun validateNoBookingOverlap(
        newStart: Long,
        newEnd: Long,
        existingStart: Long,
        existingEnd: Long
    ) {
        require(newStart > existingEnd || newEnd < existingStart) {
            "Booking period overlaps with another lottery for this campsite."
        }
    }

    /**
     * Checks that registration ends before booking starts.
     *
     * @param registrationEnd Registration end in epoch milliseconds.
     * @param bookingStart Booking start in epoch milliseconds.
     * @throws IllegalArgumentException If booking starts at or before registration end.
     */
    fun validateRegistrationBeforeBooking(
        registrationEnd: Long,
        bookingStart: Long
    ) {
        require(registrationEnd < bookingStart) {
            "Registration must end before booking starts."
        }
    }

    /**
     * Checks if lottery settings can still be updated.
     *
     * @param registrationStart Registration start in epoch milliseconds.
     * @param currentTime Current time in epoch milliseconds.
     * @throws IllegalArgumentException If registration has started.
     */
    fun validateRegistrationNotStarted(
        registrationStart: Long,
        currentTime: Long
    ) {
        require(currentTime < registrationStart) {
            "Registration has already started. Dates cannot be changed."
        }
    }
    /**
     * Checks that the number of winners is valid.
     */
    fun validateNumberOfWinners(count: Int) {
        require(count > 0) {
            "Number of winners must be positive."
        }
    }

    /**
     * Checks that registration starts in the future.
     */
    fun validateFutureRegistrationStart(
        registrationStart: Long,
        currentTime: Long
    ) {
        require(registrationStart > currentTime) {
            "Registration start must be in the future."
        }
    }

}