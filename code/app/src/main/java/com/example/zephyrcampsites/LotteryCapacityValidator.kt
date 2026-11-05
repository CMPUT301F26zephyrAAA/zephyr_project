package com.example.zephyrcampsites

/**
 * Checks if a lottery's waitlist capacity can be updated.
 */
object LotteryCapacityValidator {

    fun validateCapacityUpdate(
        oldCapacity: Int?,
        newCapacity: Int?,
        registrationStarted: Boolean
    ) {
        // Capacity must be positive if a limit is set.
        require(newCapacity == null || newCapacity > 0) {
            "Waitlist capacity must be positive."
        }

        // After registration starts, capacity can only increase.
        if (registrationStarted) {
            require(
                newCapacity != null &&
                        oldCapacity != null &&
                        newCapacity > oldCapacity
            ) {
                "Capacity can only be increased after registration starts."
            }
        }
    }
}