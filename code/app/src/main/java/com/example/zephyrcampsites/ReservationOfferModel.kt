package com.example.zephyrcampsites
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
// AI Citation [AI-23], [AI-29]
// OpenAI, ChatGPT assisted with Firestore reservation
// offer transactions, backup promotion, and validation.
// AI usage and citations documented in the GitHub Wiki.
/**
 * Handles reservation opportunities created by lottery draws.
 */
class ReservationOfferModel(
    private val firestore: FirebaseFirestore = Firebase.firestore) {
    private val waitingList = firestore.collection("waitingList")

    /**
     * Retrieves the entrant's current lottery result.
     * Returns null when the entrant has no waiting-list registration.
     */
    fun getEntrantDrawStatus(
        lotteryId: String,
        accountId: String,
        onResult: (String?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank() || accountId.isBlank()) {
            onError("Lottery or account ID is missing")
            return
        }

        val registrationId = "${lotteryId}_${accountId}"

        waitingList.document(registrationId)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onResult(null)
                    return@addOnSuccessListener
                }

                if (document.getString("lotteryId") != lotteryId ||
                    document.getString("accountId") != accountId
                ) {
                    onError("Registration information is invalid")
                    return@addOnSuccessListener
                }

                val status = document.getString("drawStatus")
                    ?: "WAITING"

                onResult(status)
            }
            .addOnFailureListener { exception ->
                onError(
                    exception.message ?: "Failed to load lottery result"
                )
            }
    }


    /**
     * Declines a lottery-winning opportunity and promotes
     * the next eligible backup, if one exists.
     */
    fun declineReservationOffer(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank() || accountId.isBlank() ||
            lotteryId.contains("/") || accountId.contains("/")) {
            onError("Invalid lottery or account ID")
            return
        }

        // Do not release an offer after a reservation has already been created.
        // The current reservation schema has no lotteryId, so block any
        // non-cancelled reservation for the same entrant and campsite.
        firestore.collection("lottery").document(lotteryId)
            .get(Source.SERVER)
            .addOnSuccessListener { lotteryDocument ->
                val campsiteId = lotteryDocument.getString("campsiteId")
                if (!lotteryDocument.exists() || campsiteId.isNullOrBlank()) {
                    onError("Lottery campsite not found")
                    return@addOnSuccessListener
                }
                firestore.collection("reservation")
                    .whereEqualTo("accountId", accountId)
                    .get(Source.SERVER)
                    .addOnSuccessListener { reservations ->
                        if (reservations.documents.any { document ->
                                document.getString("campsiteId") == campsiteId &&
                                        document.getBoolean("cancelled") != true
                            }) {
                            onError("You already have a reservation for this campsite. Cancel the reservation instead.")
                        } else {
                            executeDeclineTransaction(lotteryId, accountId, onSuccess, onError)
                        }
                    }
                    .addOnFailureListener { error ->
                        onError(error.message ?: "Could not verify existing reservations")
                    }
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Could not verify lottery information")
            }
    }

    /**
     * Atomically declines a winning offer and promotes
     * the next eligible backup without exceeding the winner limit.
     */
    private fun executeDeclineTransaction(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val lotteryRef = firestore.collection("lottery")
            .document(lotteryId)

        val resultRef = firestore.collection("lotteryDrawResults")
            .document(lotteryId)

        val entrantRef = waitingList.document(
            "${lotteryId}_${accountId}"
        )

        firestore.runTransaction { transaction ->

            // Read the latest lottery, results and winning entrant.
            val lotteryDoc = transaction.get(lotteryRef)
            val resultDoc = transaction.get(resultRef)
            val entrantDoc = transaction.get(entrantRef)

            // Validate that the lottery draw has completed.
            if (!lotteryDoc.exists() ||
                lotteryDoc.getBoolean("drawCompleted") != true ||
                lotteryDoc.getString("drawStatus") != "COMPLETED"
            ) {
                throw IllegalStateException(
                    "Lottery draw has not been completed"
                )
            }

            if (!resultDoc.exists() || !entrantDoc.exists()) {
                throw IllegalStateException(
                    "Reservation offer not found"
                )
            }

            val numberOfWinners =
                lotteryDoc.getLong("numberOfWinners")
                    ?: throw IllegalStateException(
                        "Lottery winner limit is missing"
                    )

            if (numberOfWinners < 0) {
                throw IllegalStateException(
                    "Invalid lottery winner limit"
                )
            }

            // Validate the declining entrant's registration.
            if (entrantDoc.getString("lotteryId") != lotteryId ||
                entrantDoc.getString("accountId") != accountId
            ) {
                throw IllegalStateException(
                    "Invalid entrant registration"
                )
            }

            val entrantStatus =
                entrantDoc.getString("drawStatus") ?: "WAITING"

            // Read the current winners.
            val winnerIds =
                (resultDoc.get("winnerIds") as? List<*>)
                    ?.map {
                        it as? String
                            ?: throw IllegalStateException(
                                "Invalid winner ID"
                            )
                    }
                    ?.toMutableList()
                    ?: throw IllegalStateException(
                        "Winner list is missing"
                    )

            if (winnerIds.size != winnerIds.distinct().size) {
                throw IllegalStateException(
                    "Duplicate winner IDs"
                )
            }

            // Read the original randomized backup order.
            val backupIds =
                (resultDoc.get("backupIds") as? List<*>)
                    ?.map {
                        it as? String
                            ?: throw IllegalStateException(
                                "Invalid backup ID"
                            )
                    }
                    ?: throw IllegalStateException(
                        "Backup list is missing"
                    )

            if (backupIds.size != backupIds.distinct().size ||
                backupIds.any {
                    it.isBlank() ||
                            it.contains("/") ||
                            it == "." ||
                            it == ".."
                }
            ) {
                throw IllegalStateException(
                    "Invalid backup list"
                )
            }

            // Read entrants who have already declined.
            val declinedIds =
                (resultDoc.get("declinedIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.toMutableList()
                    ?: mutableListOf()

            // Prevent invalid or repeated declines.
            if (!ReservationOfferValidator.canDecline(
                    drawCompleted = true,
                    entrantStatus = entrantStatus,
                    alreadyDeclined = accountId in declinedIds
                )
            ) {
                throw IllegalStateException(
                    "You do not have an active winning offer"
                )
            }

            if (accountId !in winnerIds) {
                throw IllegalStateException(
                    "This offer is no longer available"
                )
            }

            // Prepare references for every backup registration.
            val backupRefs = backupIds.associateWith { backupId ->
                waitingList.document("${lotteryId}_${backupId}")
            }

            // Read every backup registration before any writes.
            val backupDocs = backupRefs.mapValues { (_, ref) ->
                transaction.get(ref)
            }

            // Prepare references for every backup account.
            val backupAccountRefs =
                backupIds.associateWith { backupId ->
                    firestore.collection("accounts")
                        .document(backupId)
                }

            // Read backup accounts inside the same transaction.
            val backupAccountDocs =
                backupAccountRefs.mapValues { (_, ref) ->
                    transaction.get(ref)
                }

            // Remove the declining winner from the active list.
            winnerIds.remove(accountId)
            declinedIds.add(accountId)

            // Remove permanently invalid backup registrations.
            // Preserve temporarily unavailable accounts in their
            // original order so they can be rechecked later.
            val remainingBackupIds = backupIds.filter { backupId ->

                val backupDoc = backupDocs.getValue(backupId)
                val backupAccount =
                    backupAccountDocs.getValue(backupId)

                backupDoc.exists() &&
                        backupDoc.getString("lotteryId") == lotteryId &&
                        backupDoc.getString("accountId") == backupId &&
                        backupDoc.getString("drawStatus") == "BACKUP" &&
                        backupAccount.exists() &&
                        backupId !in winnerIds &&
                        backupId !in declinedIds

            }.toMutableList()

            // Select the first eligible backup in the original order.
            var nextBackupId: String? = null

            if (ReservationOfferValidator.canIssueReplacementOffer(
                    currentWinnerCount = winnerIds.size,
                    numberOfWinners = numberOfWinners
                )
            ) {
                nextBackupId =
                    remainingBackupIds.firstOrNull { backupId ->

                        val backupDoc =
                            backupDocs.getValue(backupId)

                        val backupAccount =
                            backupAccountDocs.getValue(backupId)

                        ReservationOfferValidator.canPromote(
                            registrationExists = backupDoc.exists(),

                            registrationMatches =
                                backupDoc.getString("lotteryId") ==
                                        lotteryId &&
                                        backupDoc.getString("accountId") ==
                                        backupId,

                            entrantStatus =
                                backupDoc.getString("drawStatus"),

                            accountExists = backupAccount.exists(),

                            deletionPending =
                                backupAccount.getBoolean(
                                    "deletionPending"
                                ) == true
                        )
                    }
            }

            // All Firestore reads are complete.
            // Begin transaction writes.

            // Record the original winner's decline.
            transaction.update(
                entrantRef,
                mapOf(
                    "drawStatus" to "DECLINED",
                    "backupPosition" to 0
                )
            )

            // Promote the next eligible backup, if available.
            if (nextBackupId != null) {

                val nextBackupRef =
                    backupRefs.getValue(nextBackupId)

                transaction.update(
                    nextBackupRef,
                    mapOf(
                        "drawStatus" to "WINNER",
                        "backupPosition" to 0
                    )
                )

                remainingBackupIds.remove(nextBackupId)
                winnerIds.add(nextBackupId)
            }

            // Update the remaining backup positions.
            remainingBackupIds.forEachIndexed { index, backupId ->

                val backupRef = backupRefs.getValue(backupId)

                transaction.update(
                    backupRef,
                    "backupPosition",
                    index + 1
                )
            }

            // Persist the updated lists and counts together.
            transaction.update(
                resultRef,
                mapOf(
                    "winnerIds" to winnerIds,
                    "backupIds" to remainingBackupIds,
                    "declinedIds" to declinedIds,
                    "winnerCount" to winnerIds.size,
                    "backupCount" to remainingBackupIds.size
                )
            )

            true
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(
                    exception.message
                        ?: "Failed to decline reservation offer"
                )
            }
    }


    /**
     * Retrieves the account IDs of entrants who declined
     * their lottery-winning reservation opportunity.
     */
    fun getDeclinedEntrants(
        lotteryId: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank() || lotteryId.contains("/")) {
            onError("Invalid lottery ID")
            return
        }

        firestore.collection("lotteryDrawResults")
            .document(lotteryId)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onSuccess(emptyList())
                    return@addOnSuccessListener
                }

                val declinedIds = document.get("declinedIds") as? List<*>

                if (declinedIds == null) {
                    onSuccess(emptyList())
                } else {
                    onSuccess(declinedIds.filterIsInstance<String>())
                }
            }
            .addOnFailureListener { exception ->
                onError(
                    exception.message ?: "Failed to load declined entrants"
                )
            }
    }

}
