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

}