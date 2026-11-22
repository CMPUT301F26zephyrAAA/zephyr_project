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

    // Keep track of draws already started by this app instance.
    private val activeDraws = mutableSetOf<String>()

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

                // Make sure no registration was skipped while loading.
                if (entrants.size != documents.size()) {
                    onError(
                        "Some lottery registrations could not be loaded. " +
                                "Please check the waiting list."
                    )
                    return@addOnSuccessListener
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
                // Check whether the lottery draw is already completed.
                val drawCompleted =
                    document.getBoolean("drawCompleted") ?: false

                // Check if another draw has already started.
                val drawStatus = document.getString("drawStatus") ?: "PENDING"

                // Only allow a draw that has not started yet.
                val ready = drawStatus == "PENDING" &&
                        LotteryDraw.canStartDraw(
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
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        val lotteryRef = lotteries.document(lotteryId)

        // Get the document where draw results are stored.
        val resultRef = drawResults.document(lotteryId)
        // Give this draw attempt its own unique ID.
        val drawAttemptId = java.util.UUID.randomUUID().toString()

        firestore.runTransaction { transaction ->

            // Read the latest lottery data.
            val document = transaction.get(lotteryRef)

            // Check whether this lottery already has saved results.
            val resultDocument = transaction.get(resultRef)

            if (resultDocument.exists()) {
                throw IllegalStateException(
                    "Lottery results already exist. Cannot start another draw."
                )
            }

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

            // Mark the lottery as processing and record this draw attempt.
            transaction.update(
                lotteryRef,
                mapOf(
                    "drawStatus" to "PROCESSING",
                    "drawAttemptId" to drawAttemptId
                )
            )

            true
        }
            .addOnSuccessListener {
                // Return the ID of the draw attempt that claimed the lottery.
                onSuccess(drawAttemptId)
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
        drawAttemptId: String,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        // Make sure the draw attempt ID was provided.
        if (drawAttemptId.isBlank()) {
            onError("Draw attempt ID is missing")
            return
        }

        val lotteryRef = lotteries.document(lotteryId)
        // Get the saved draw results document.
        val resultRef = drawResults.document(lotteryId)

        firestore.runTransaction { transaction ->
            // Get the latest draw status.
            val document = transaction.get(lotteryRef)

            // Check whether results were already saved.
            val resultDocument = transaction.get(resultRef)

            if (resultDocument.exists()) {
                throw IllegalStateException(
                    "Draw results already exist. Lottery cannot be reset."
                )
            }

            if (!document.exists()) {
                throw IllegalStateException("Lottery not found")
            }

            val drawStatus = document.getString("drawStatus")
            val drawCompleted = document.getBoolean("drawCompleted") ?: false

            // Only reset a draw that is still processing.
            if (drawStatus != "PROCESSING" || drawCompleted) {
                throw IllegalStateException("Lottery cannot be reset")
            }

            // Check that this attempt owns the lottery draw.
            val savedAttemptId = document.getString("drawAttemptId")

            if (drawAttemptId.isBlank() || savedAttemptId != drawAttemptId) {
                throw IllegalStateException(
                    "This draw attempt cannot reset the lottery"
                )
            }

            // Reset the lottery so the draw can be tried again.
            transaction.update(
                lotteryRef,
                mapOf(
                    "drawStatus" to "PENDING",
                    "drawAttemptId" to ""
                )
            )

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

                // Make sure every registration is eligible for this draw.
                if (eligibleEntrants.size != entrants.size) {
                    onError(
                        "Some registrations are not eligible. " +
                                "Please check the waiting list before drawing."
                    )
                    return@getLotteryEntrants
                }

                // Make sure each account has only one registration.
                val accountIds = eligibleEntrants.map { it.accountId }

                if (accountIds.size != accountIds.toSet().size) {
                    onError("Duplicate registrations found in the waiting list")
                    return@getLotteryEntrants
                }

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
     * Runs the lottery draw and saves the results.
     */
    fun runLotteryDraw(
        lotteryId: String,
        numberOfWinners: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Claim the lottery before selecting winners.
        claimLotteryDraw(
            lotteryId,
            onSuccess = { drawAttemptId ->

                // Prepare the winners and backup list.
                prepareLotteryDraw(
                    lotteryId,
                    numberOfWinners,
                    onSuccess = { winners, backups ->

                        // Save the results using the same draw attempt ID.
                        saveLotteryDraw(
                            lotteryId,
                            drawAttemptId,
                            winners,
                            backups,
                            numberOfWinners,
                            onSuccess,
                            onError
                        )
                    },
                    onError = { error ->
                        // Keep the draw locked if preparation fails.
                        // This prevents another draw from starting accidentally.
                        onError(
                            "Lottery draw preparation failed: $error. " +
                                    "The lottery may need recovery before trying again."
                        )
                    }
                )
            },
            onError = onError
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

    /**
     * Gets the Firestore references for the entrants in a lottery draw.
     */
    private fun getDrawEntrantRefs(
        updates: Map<String, Map<String, Any>>
    ) = updates.keys.map { entrantId ->
        waitingList.document(entrantId)
    }

    /**
     * Checks that the prepared draw results are valid before saving.
     */
    private fun validateDrawResults(
        lotteryId: String,
        winners: List<WaitingUser>,
        backups: List<WaitingUser>,
        numberOfWinners: Int
    ) {
        // A lottery cannot have a negative number of winners.
        require(numberOfWinners >= 0) {
            "Number of winners cannot be negative"
        }

        // The draw cannot select more winners than allowed.
        require(winners.size <= numberOfWinners) {
            "Too many winners selected"
        }

        val allEntrants = winners + backups

        // Make sure all entrants belong to this lottery.
        require(allEntrants.all { it.lotteryId == lotteryId }) {
            "An entrant belongs to a different lottery"
        }

        // Make sure every entrant has an account and document ID.
        require(allEntrants.all {
            it.id.isNotBlank() && it.accountId.isNotBlank()
        }) {
            "An entrant has a missing ID"
        }

        // Each account can only appear once in the draw.
        require(allEntrants.map { it.accountId }.distinct().size == allEntrants.size) {
            "Duplicate account found in draw results"
        }
    }

    /**
     * Prepares the lottery draw summary for Firebase.
     * The results are saved after the draw is validated.
     */
    private fun buildDrawSummary(
        lotteryId: String,
        winners: List<WaitingUser>,
        backups: List<WaitingUser>,
        completedAt: Long
    ): Map<String, Any> {

        return mapOf(
            "lotteryId" to lotteryId,
            "winnerIds" to winners.map { it.accountId },
            "backupIds" to backups.map { it.accountId },
            "winnerCount" to winners.size,
            "backupCount" to backups.size,
            "completedAt" to completedAt
        )
    }

    /**
     * Saves the lottery draw results in Firebase.
     * The transaction will check the lottery before updating it.
     */
    fun saveLotteryDraw(
        lotteryId: String,
        drawAttemptId: String,
        winners: List<WaitingUser>,
        backups: List<WaitingUser>,
        numberOfWinners: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()) {
            onError("Lottery ID is missing")
            return
        }

        // Check the results before trying to save them.
        try {
            validateDrawResults(
                lotteryId,
                winners,
                backups,
                numberOfWinners
            )
        } catch (exception: IllegalArgumentException) {
            onError(exception.message ?: "Invalid lottery results")
            return
        }

        // Get the Firebase documents needed for this draw.
        val lotteryRef = lotteries.document(lotteryId)
        val resultRef = drawResults.document(lotteryId)

        // Prepare the winner and backup updates.
        val updates = buildDrawUpdates(winners, backups)
        val entrantRefs = getDrawEntrantRefs(updates)

        // Start a transaction to save the draw safely.
        firestore.runTransaction { transaction ->

            // Read the latest lottery and result documents.
            val lotteryDocument = transaction.get(lotteryRef)
            val resultDocument = transaction.get(resultRef)

            if (!lotteryDocument.exists()) {
                throw IllegalStateException("Lottery not found")
            }

            val drawCompleted =
                lotteryDocument.getBoolean("drawCompleted") ?: false

            val drawStatus =
                lotteryDocument.getString("drawStatus") ?: "PENDING"

            // Only save a draw that is currently processing.
            if (drawCompleted || drawStatus != "PROCESSING") {
                throw IllegalStateException("Lottery is not ready to save")
            }

            // Check that this draw attempt owns the lottery.
            val savedAttemptId = lotteryDocument.getString("drawAttemptId")

            if (drawAttemptId.isBlank() || savedAttemptId != drawAttemptId) {
                throw IllegalStateException(
                    "This draw attempt does not match the active lottery draw"
                )
            }

            // Make sure registration has ended before saving the draw.
            val registrationEnd = lotteryDocument.getLong("registrationEnd")
                ?: throw IllegalStateException("Registration end date is missing")

            if (System.currentTimeMillis() < registrationEnd) {
                throw IllegalStateException(
                    "Registration is still open. Cannot save the draw."
                )
            }

            // Do not overwrite results from an earlier draw.
            if (resultDocument.exists()) {
                throw IllegalStateException("Draw results already exist")
            }

            // Check that the configured winner count has not changed.
            val configuredWinners = lotteryDocument.getLong("numberOfWinners")
                ?: throw IllegalStateException("Winner count is missing")

            if (configuredWinners != numberOfWinners.toLong()) {
                throw IllegalStateException("Lottery winner count has changed")
            }

            // Read every entrant before making any writes.
            val entrantDocuments = entrantRefs.map { entrantRef ->
                transaction.get(entrantRef)
            }

            // Match each entrant using their Firestore document ID.
            val selectedEntrants = (winners + backups).associateBy { it.id }

            entrantDocuments.forEach { document ->

                // Find the selected entrant for this document.
                val selectedEntrant = selectedEntrants[document.id]
                    ?: throw IllegalStateException("Entrant not found in draw")

                // Make sure the account still matches.
                if (document.getString("accountId") != selectedEntrant.accountId) {
                    throw IllegalStateException(
                        "An entrant's account information has changed"
                    )
                }
            }

            // Check that all selected entrants still exist and are waiting.
            entrantDocuments.forEach { document ->
                if (!document.exists()) {
                    throw IllegalStateException("An entrant is no longer registered")
                }

                if (document.getString("lotteryId") != lotteryId ||
                    (document.getString("drawStatus") ?: "WAITING") != "WAITING"
                ) {
                    throw IllegalStateException("An entrant is no longer eligible")
                }
            }

            // Check that the number of registered entrants has not changed.
            val entrantCount = lotteryDocument.getLong("entrantCount")
                ?: throw IllegalStateException("Lottery entrant count is missing")

            val totalSelected = winners.size + backups.size

            if (entrantCount != totalSelected.toLong()) {
                throw IllegalStateException(
                    "Waiting list has changed. Please prepare the draw again."
                )
            }

            // Save each entrant's winner or backup status.
            updates.forEach { (entrantId, fields) ->
                val entrantRef = waitingList.document(entrantId)
                transaction.update(entrantRef, fields)
            }

            // Record when the draw was completed.
            val completedAt = System.currentTimeMillis()

            // Save the winners and backup order.
            val summary = buildDrawSummary(
                lotteryId,
                winners,
                backups,
                completedAt
            )

            transaction.set(resultRef, summary)

            // Mark the lottery as completed.
            transaction.update(
                lotteryRef,
                mapOf(
                    "drawCompleted" to true,
                    "drawCompletedAt" to completedAt,
                    "drawStatus" to "COMPLETED"
                )
            )

            true
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to save lottery draw")
            }


    }
    /**
     * Checks for lotteries that are ready for drawing.
     * Avoids starting the same draw multiple times locally.
     */
    fun checkPendingLotteryDraws(lotteries: List<Lottery>) {

        val currentTime = System.currentTimeMillis()

        lotteries.forEach { lottery ->

            // Only start draws after registration ends.
            if (lottery.registrationEnd <= currentTime &&
                !lottery.drawCompleted &&
                lottery.drawStatus == "PENDING" &&
                activeDraws.add(lottery.id)
            ) {
                runLotteryDraw(
                    lotteryId = lottery.id,
                    numberOfWinners = lottery.numberOfWinners,

                    onSuccess = {
                        // Remove the draw from local tracking.
                        activeDraws.remove(lottery.id)

                        android.util.Log.d(
                            "LotteryDraw",
                            "Lottery draw completed: ${lottery.id}"
                        )
                    },

                    onError = { error ->
                        // Remove the draw so future checks can retry.
                        activeDraws.remove(lottery.id)

                        android.util.Log.e(
                            "LotteryDraw",
                            "Lottery draw failed: $error"
                        )
                    }
                )
            }
        }
    }

}