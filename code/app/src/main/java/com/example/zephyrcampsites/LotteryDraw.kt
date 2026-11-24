package com.example.zephyrcampsites

/**
 * Handles the random selection of lottery winners and backup entrants.
 */
object LotteryDraw {

    /**
     * Randomly selects winners without selecting the same entrant twice.
     * Entrants who are not winners are placed in a random backup order.
     */
    fun selectWinners(
        entrants: List<WaitingUser>,
        numberOfWinners: Int
    ): Pair<List<WaitingUser>, List<WaitingUser>> {

        // Shuffle entrants so everyone has a random position.
        val shuffledEntrants = entrants
            .distinctBy { it.accountId }
            .shuffled()

        // Select only the allowed number of winners.
        val winners = shuffledEntrants.take(
            numberOfWinners.coerceAtLeast(0)
        )

        // Everyone remaining becomes a backup entrant.
        val backups = shuffledEntrants.drop(winners.size)

        return Pair(winners, backups)
    }

    /**
     * Filters registrations before the lottery draw.
     * Only entrants still waiting in this lottery are included.
     */
    fun getEligibleEntrants(
        entrants: List<WaitingUser>,
        lotteryId: String
    ): List<WaitingUser> {

        return entrants.filter { entrant ->

            // Check that the entrant belongs to this lottery.
            entrant.lotteryId == lotteryId &&

                    // Make sure the entrant has an account ID.
                    entrant.accountId.isNotBlank() &&

                    // Only include entrants who have not been drawn yet.
                    entrant.drawStatus == "WAITING"
        }
    }

    /**
     * Checks whether a lottery is ready for the winner draw.
     */
    fun canStartDraw(
        registrationEnd: Long,
        drawCompleted: Boolean,
        currentTime: Long
    ): Boolean {

        // Registration must be closed before drawing winners.
        if (currentTime < registrationEnd) {
            return false
        }

        // Do not allow a lottery to be drawn again.
        if (drawCompleted) {
            return false
        }

        return true
    }

    /**
     * Checks whether a lottery draw can move to another status.
     */
    fun canChangeDrawStatus(
        currentStatus: String,
        newStatus: String
    ): Boolean {

        return when (currentStatus) {
            // Start a new lottery draw.
            "PENDING" -> newStatus == "PROCESSING"

            // Complete the draw or reset it after a safe recovery check.
            "PROCESSING" -> newStatus == "COMPLETED" ||
                    newStatus == "PENDING"

            // A completed draw cannot be changed.
            else -> false
        }
    }

}