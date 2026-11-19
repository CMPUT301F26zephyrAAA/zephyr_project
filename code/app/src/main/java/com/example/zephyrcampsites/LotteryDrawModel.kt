package com.example.zephyrcampsites

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Handles reading lottery entrants and saving draw results in Firebase.
 */
class LotteryDrawModel(
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) {

    private val lotteries = firestore.collection("lottery")
    private val waitingList = firestore.collection("waitingList")
    // Stores the results of each lottery draw.
    private val drawResults = firestore.collection("lotteryDrawResults")

    /**
     * Gets all registered entrants for a specific lottery.
     *
     * @param lotteryId ID of the lottery.
     * @param onSuccess Returns the registered entrants.
     * @param onError Returns an error if Firebase fails.
     */
    fun getLotteryEntrants(
        lotteryId: String,
        onSuccess: (List<WaitingUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        // Check that the lottery ID is provided.
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        // Find registrations belonging to this lottery.
        waitingList
            .whereEqualTo("lotteryId", lotteryId)
            .get()
            .addOnSuccessListener { documents ->

                // Convert Firebase documents into WaitingUser objects.
                // Keep the Firestore document ID with each entrant.
                val entrants = documents.mapNotNull { document ->
                    document.toObject(WaitingUser::class.java)?.let { entrant ->
                        WaitingUser(document.id).also { waitingUser ->
                            waitingUser.lotteryId = entrant.lotteryId
                            waitingUser.accountId = entrant.accountId
                            waitingUser.joinedAt = entrant.joinedAt
                            waitingUser.drawStatus = entrant.drawStatus
                            waitingUser.backupPosition = entrant.backupPosition
                        }
                    }
                }

                onSuccess(entrants)
            }
            .addOnFailureListener { exception ->
                onError(
                    exception.message ?: "Failed to load lottery entrants"
                )
            }
    }

    /**
     * Checks whether a lottery is ready for its winner draw.
     */
    fun checkDrawReady(
        lotteryId: String,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        // Get the latest lottery information from Firebase.
        lotteries.document(lotteryId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    onError("Lottery not found")
                    return@addOnSuccessListener
                }

                val registrationEnd = document.getLong("registrationEnd")
                if (registrationEnd == null) {
                    onError("Registration end date is missing")
                    return@addOnSuccessListener
                }

                val drawCompleted =
                    document.getBoolean("drawCompleted") ?: false

                // Check whether registration has ended.
                val ready = LotteryDraw.canStartDraw(
                    registrationEnd,
                    drawCompleted,
                    System.currentTimeMillis()
                )

                onSuccess(ready)
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to check draw status")
            }}
        /**
         * Loads the latest lottery settings before starting a draw.
         */
        fun getLotteryForDraw(
            lotteryId: String,
            onSuccess: (Lottery) -> Unit,
            onError: (String) -> Unit
        ) {
            if (lotteryId.isBlank()) {
                onError("Lottery ID is missing")
                return
            }

            // Read the lottery information from Firebase.
            lotteries.document(lotteryId)
                .get()
                .addOnSuccessListener { document ->

                    if (!document.exists()) {
                        onError("Lottery not found")
                        return@addOnSuccessListener
                    }

                    // Convert the Firestore document into a Lottery object.
                    val lottery = document.toObject(Lottery::class.java)

                    if (lottery == null) {
                        onError("Failed to load lottery")
                        return@addOnSuccessListener
                    }

                    onSuccess(lottery)
                }
                .addOnFailureListener { exception ->
                    onError(
                        exception.message ?: "Failed to load lottery"
                    )
                }

    }
    /**
     * Claims a lottery for processing so another device
     * cannot start the same draw at the same time.
     */
    fun claimLotteryDraw(
        lotteryId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        val lotteryRef = lotteries.document(lotteryId)

        firestore.runTransaction { transaction ->

            // Read the latest lottery data.
            val document = transaction.get(lotteryRef)

            if (!document.exists()) {
                throw IllegalStateException("Lottery not found")
            }

            val registrationEnd = document.getLong("registrationEnd")
                ?: throw IllegalStateException("Registration end is missing")

            val drawCompleted = document.getBoolean("drawCompleted") ?: false
            val drawStatus = document.getString("drawStatus") ?: "PENDING"

            // Only claim lotteries that are ready for a draw.
            if (!LotteryDraw.canStartDraw(
                    registrationEnd,
                    drawCompleted,
                    System.currentTimeMillis()
                ) || drawStatus != "PENDING"
            ) {
                throw IllegalStateException("Lottery is not ready for drawing")
            }

            // Mark the lottery as being processed.
            transaction.update(lotteryRef, "drawStatus", "PROCESSING")

            true
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to claim lottery draw")
            }
    }

    /**
     * Resets a lottery if the draw fails before results are saved.
     */
    fun releaseLotteryDraw(
        lotteryId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        val lotteryRef = lotteries.document(lotteryId)

        firestore.runTransaction { transaction ->
            // Get the latest draw status.
            val document = transaction.get(lotteryRef)

            if (!document.exists()) {
                throw IllegalStateException("Lottery not found")
            }

            val drawStatus = document.getString("drawStatus")
            val drawCompleted = document.getBoolean("drawCompleted") ?: false

            // Only reset a draw that is still processing.
            if (drawStatus != "PROCESSING" || drawCompleted) {
                throw IllegalStateException("Lottery cannot be reset")
            }

            transaction.update(lotteryRef, "drawStatus", "PENDING")

            true
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to reset lottery draw")
            }
    }
    /**
     * Gets eligible entrants and randomly selects winners and backups.
     */
    fun prepareLotteryDraw(
        lotteryId: String,
        numberOfWinners: Int,
        onSuccess: (List<WaitingUser>, List<WaitingUser>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        // Load all entrants registered for this lottery.
        getLotteryEntrants(
            lotteryId,
            onSuccess = { entrants ->

                // Only include entrants who are still waiting.
                val eligibleEntrants = LotteryDraw.getEligibleEntrants(
                    entrants,
                    lotteryId
                )

                // Randomly select winners and arrange the backup list.
                val (winners, backups) = LotteryDraw.selectWinners(
                    eligibleEntrants,
                    numberOfWinners
                )

                onSuccess(winners, backups)
            },
            onError = { error ->
                onError(error)
            }
        )
    }

    /**
     * Prepares the Firestore updates for winners and backups.
     */
    private fun buildDrawUpdates(
        winners: List<WaitingUser>,
        backups: List<WaitingUser>
    ): Map<String, Map<String, Any>> {

        val updates = mutableMapOf<String, Map<String, Any>>()

        // Mark selected entrants as winners.
        winners.forEach { entrant ->
            updates[entrant.id] = mapOf(
                "drawStatus" to "WINNER",
                "backupPosition" to 0
            )
        }

        // Give each backup entrant a position starting from 1.
        backups.forEachIndexed { index, entrant ->
            updates[entrant.id] = mapOf(
                "drawStatus" to "BACKUP",
                "backupPosition" to index + 1
            )
        }

        return updates
    }

}