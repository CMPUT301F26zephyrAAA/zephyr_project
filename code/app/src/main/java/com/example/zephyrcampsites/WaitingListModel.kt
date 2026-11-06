package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Model responsible for reading & writing lottery waiting list registration in firebase firestore
 */
class WaitingListModel : WaitingListGateway {

    private val firestore = Firebase.firestore
    private val waitingList = firestore.collection("waitingList")
    private val lotteries = firestore.collection("lottery")


    /**
     * Creates a consistent document Id for an entrant in a lottery
     * Ensures that the same entrant cannot have multiple registration documents for the same lottery
     */
    private fun registrationId(
        lotteryId: String,
        accountId: String
    ): String {
        return "${lotteryId}_${accountId}"
    }

    /**
     * Checks whether an entrant is already on a lotterys waiting list
     */
    override fun isEntrantJoined(
        lotteryId: String,
        accountId: String,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val id = registrationId(lotteryId, accountId)
        waitingList.document(id)
            .get()
            .addOnSuccessListener { document ->
                onResult(document.exists())
            }
            .addOnFailureListener { exception ->
                onError(
                    exception.message ?: "Failed to check the waiting list"
                )
            }
    }

    /**
     * Adds an entrant to a lottery waiting list
     * Fire store transaction checks - lottery exists, registration is currently open, entrant has not already joined
     *
     * @param lotteryId ID of the lottery
     * @param accountId Device identified entrant account ID
     * @param onSuccess Called after Firebase confirms the write
     * @param onError Called when joining fails
     */
    override fun joinWaitingList(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank() || accountId.isBlank()) {
            onError("Lottery or Account Id is missing")
            return
        }

        val id = registrationId(lotteryId, accountId)
        val registrationRef = waitingList.document(id)
        val lotteryRef = lotteries.document(lotteryId)

        firestore.runTransaction { transaction ->
            val lotteryDocument = transaction.get(lotteryRef)
            if (!lotteryDocument.exists()) {
                throw IllegalStateException("Lottery not found")
            }

            val registrationStart = lotteryDocument.getLong("registrationStart")
                ?: throw IllegalStateException("Lottery registration start is missing")

            val registrationEnd = lotteryDocument.getLong("registrationEnd")
                ?: throw IllegalStateException("Lottery registration end is missing")

            val currentTime = System.currentTimeMillis()
            val existingRegistration = transaction.get(registrationRef)

            val capacity = lotteryDocument.getLong("waitlistCapacity")
            val entrantCount = lotteryDocument.getLong("entrantCount") ?: 0L

            if (capacity != null && entrantCount >= capacity) {
                throw IllegalStateException("Lottery waiting list is full")
            }

            if (currentTime < registrationStart) {
                throw IllegalStateException("Lottery registration has not started")
            }

            if (currentTime >= registrationEnd) {
                throw IllegalStateException("Lottery registration has closed")
            }

            if (!WaitingListValidator.canJoin(
                    currentTime = currentTime,
                    registrationStart = registrationStart,
                    registrationEnd = registrationEnd,
                    alreadyJoined = existingRegistration.exists()
                )
            ) {
                throw IllegalStateException("You have already joined this lottery")
            }

            val registration = WaitingUser(id).apply {
                this.lotteryId = lotteryId
                this.accountId = accountId
                this.joinedAt = currentTime
            }

            transaction.set(
                registrationRef,
                registration
            )

            transaction.update(
                lotteryRef,
                "entrantCount",
                entrantCount + 1
            )

            true
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to join waiting list")
            }
    }

    /**
     * Removes an entrant from a lottery waiting list
     * Temp halfway checkpoint " if drawCompleted is missing, the draw feature has not been integrated yet so leaving is permitted
     * Needs to be done later - (Final checkpoint)Integrate with the actual lottery draw status and prevent leaving after a draw.
     */
    override fun leaveWaitingList(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank() || accountId.isBlank()) {
            onError("Lottery or Account Id is missing")
            return
        }

        val id = registrationId(lotteryId, accountId)
        val registrationRef = waitingList.document(id)
        val lotteryRef = lotteries.document(lotteryId)

        firestore.runTransaction { transaction ->
            val lotteryDocument = transaction.get(lotteryRef)
            if (!lotteryDocument.exists()) {
                throw IllegalStateException("Lottery not found")
            }

            val drawCompleted = lotteryDocument.getBoolean("drawCompleted")
            //temporary compatibility until draw logic is implemented
            if (drawCompleted == true) {
                throw IllegalStateException("You cannot leave after the lottery draw")
            }

            val registration = transaction.get(registrationRef)
            if (!registration.exists()) {
                throw IllegalStateException("You are not on this lottery's waiting list")
            }

            val entrantCount = lotteryDocument.getLong("entrantCount") ?: 0L

            transaction.delete(registrationRef)

            transaction.update(
                lotteryRef,
                "entrantCount",
                (entrantCount - 1).coerceAtLeast(0L)
            )
            true
        }
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception.message ?: "Failed to leave waiting list.")
            }
    }
}