package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore

/**
 * Model in charge of retrieving reservation records from Firestore
 */
class ReservationsModel(private val firestore: FirebaseFirestore = Firebase.firestore) {
    private val reservations = firestore.collection("reservation")

    /**
     * Retrieve reservations belonging to specified entrant
     * @param accountId Current entrant's device linked accountID
     * @param onSuccess called with the entrants reservations
     * @param onError called if retrieval or data validation fails
     */
    // AI Citation [AI-14]
    // OpenAI, ChatGPT was used to review Firestore reservation retrieval, validation, and error handling.
    // AI usage and citation documented in the GitHub Wiki.
    fun getEntrantReservations(
        accountId: String,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (accountId.isBlank() || accountId.contains("/") || accountId == "." || accountId == "..") {
            onError(IllegalArgumentException("Invalid account ID."))
            return
        }

        reservations
            .whereEqualTo("accountId", accountId)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                try {

                    val results = snapshot.documents.mapNotNull { document ->

                        // Cancelled reservations are not shown in active or past lists.
                        if (document.getBoolean("cancelled") == true) {
                            return@mapNotNull null
                        }

                        // Skip reservations with missing or invalid dates.
                        val start = document.getTimestamp("bookedPeriodStart")
                            ?: return@mapNotNull null

                        val end = document.getTimestamp("bookedPeriodEnd")
                            ?: return@mapNotNull null

                        if (!start.toDate().before(end.toDate())) {
                            return@mapNotNull null
                        }

                        // Skip reservations with missing required fields.
                        val campsiteId = document.getString("campsiteId")
                            ?: return@mapNotNull null

                        if (campsiteId.isBlank()) {
                            return@mapNotNull null
                        }

                        val cancelled = document.getBoolean("cancelled")
                            ?: return@mapNotNull null

                        ReservedUser(
                            id = document.id,
                            accountId = accountId,
                            campsiteId = campsiteId,
                            bookedPeriodStart = start.toDate(),
                            bookedPeriodEnd = end.toDate(),
                            cancelled = cancelled
                        )
                    }
                    onSuccess(results)
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }
}
