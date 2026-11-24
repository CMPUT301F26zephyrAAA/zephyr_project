package com.example.zephyrcampsites

/**
 * Checks if a lottery's waitlist capacity can be updated.
 */
object LotteryCapacityValidator {

    fun validateCapacityUpdate(
        oldCapacity: Int?,
        newCapacity: Int?,
        registrationStarted: Boolean,
        entrantCount: Long = 0L
    ) {
        // Capacity must be positive if a limit is set.
        require(newCapacity == null || newCapacity > 0) {
            "Waitlist capacity must be positive."
        }

        // Do not allow a capacity smaller than the current number of entrants.
        require(newCapacity == null || newCapacity.toLong() >= entrantCount) {
            "Capacity cannot be less than the current entrant count."
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