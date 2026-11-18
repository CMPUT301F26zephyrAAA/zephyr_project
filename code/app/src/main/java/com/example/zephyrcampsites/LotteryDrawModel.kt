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
                val entrants = documents.mapNotNull { document ->
                    document.toObject(WaitingUser::class.java)
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
            }
    }

}