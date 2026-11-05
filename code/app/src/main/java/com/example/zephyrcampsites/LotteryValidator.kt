package com.example.zephyrcampsites

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
     */
    fun validateBookingPeriod(start: Long, end: Long) {
        require(start < end) {
            "Booking end must be after booking start."
        }
    }

    /**
     * Checks if two booking periods overlap.
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