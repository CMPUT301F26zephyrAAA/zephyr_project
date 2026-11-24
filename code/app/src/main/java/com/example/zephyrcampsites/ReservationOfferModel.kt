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

        val lotteryRef = firestore.collection("lottery")
            .document(lotteryId)
        val resultRef = firestore.collection("lotteryDrawResults")
            .document(lotteryId)
        val entrantRef = waitingList.document("${lotteryId}_${accountId}")


        // Read the latest lottery results inside the transaction.
        firestore.runTransaction { transaction ->

            val lotteryDoc = transaction.get(lotteryRef)
            val resultDoc = transaction.get(resultRef)
            val entrantDoc = transaction.get(entrantRef)

            if (!resultDoc.exists()) {
                throw IllegalStateException(
                    "Lottery draw results not found"
                )
            }

            // Get the latest backup list from this transaction.
            val backupIds = (resultDoc.get("backupIds") as? List<*>)
                ?.map {
                    it as? String
                        ?: throw IllegalStateException("Invalid backup ID")
                }
                ?: throw IllegalStateException("Backup list is missing")

            if (backupIds.size != backupIds.distinct().size ||
                backupIds.any { it.isBlank() || it.contains("/") }
            ) {
                throw IllegalStateException("Invalid backup list")
            }

            // Read every backup document before making any writes.
            val backupRefs = backupIds.associateWith { backupId ->
                waitingList.document("${lotteryId}_${backupId}")
            }

            val backupDocs = backupRefs.mapValues { (_, ref) ->
                transaction.get(ref)
            }

            if (!lotteryDoc.exists() ||
                        lotteryDoc.getBoolean("drawCompleted") != true ||
                        lotteryDoc.getString("drawStatus") != "COMPLETED") {
                        throw IllegalStateException(
                            "Lottery draw has not been completed"
                        )
                    }

                    if (!resultDoc.exists() || !entrantDoc.exists()) {
                        throw IllegalStateException(
                            "Reservation offer not found"
                        )
                    }

                    val entrantStatus = entrantDoc.getString("drawStatus") ?: "WAITING"

                    if (entrantDoc.getString("lotteryId") != lotteryId ||
                        entrantDoc.getString("accountId") != accountId) {
                        throw IllegalStateException("Invalid entrant registration")
                    }

                    val hasAlreadyDeclined = accountId in (
                            (resultDoc.get("declinedIds") as? List<*>)
                                ?.filterIsInstance<String>()
                                ?: emptyList()
                            )

                    if (!ReservationOfferValidator.canDecline(
                            drawCompleted = lotteryDoc.getBoolean("drawCompleted") == true,
                            entrantStatus = entrantStatus,
                            alreadyDeclined = hasAlreadyDeclined
                        )
                    ) {
                        throw IllegalStateException(
                            "You do not have an active winning offer"
                        )
                    }

                    val winnerIds =
                        (resultDoc.get("winnerIds") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?.toMutableList()
                            ?: throw IllegalStateException(
                                "Winner list is missing"
                            )

                    val remainingBackupIds = backupIds.toMutableList()

                    val declinedIds =
                        (resultDoc.get("declinedIds") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?.toMutableList()
                            ?: mutableListOf()

                    if (accountId !in winnerIds ||
                        accountId in declinedIds) {
                        throw IllegalStateException(
                            "This offer is no longer available"
                        )
                    }

                    // Select the first backup in the original order.
                    val nextBackupId = backupIds.firstOrNull()

                    if (nextBackupId != null) {
                        val backupDoc = backupDocs[nextBackupId]

                        if (backupDoc == null ||
                            !backupDoc.exists() ||
                            backupDoc.getString("lotteryId") != lotteryId ||
                            backupDoc.getString("accountId") != nextBackupId ||
                            backupDoc.getString("drawStatus") != "BACKUP"
                        ) {
                            throw IllegalStateException(
                                "Next backup is not eligible. Please review the draw results."
                            )
                        }
                    }


            // Record the decline.
                    transaction.update(
                        entrantRef,
                        "drawStatus",
                        "DECLINED"
                    )

                    winnerIds.remove(accountId)
                    declinedIds.add(accountId)

                    // Transfer the opportunity to the next backup.
                    if (nextBackupId != null) {
                        val nextBackupRef = backupRefs.getValue(nextBackupId)

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

                    // Save the updated lists together.
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
                    exception.message ?: "Failed to decline reservation offer"
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
