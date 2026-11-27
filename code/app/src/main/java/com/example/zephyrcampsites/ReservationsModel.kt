package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Model in charge of retrieving reservation records from Firestore
 */
class ReservationsModel(private val firestore: FirebaseFirestore = Firebase.firestore) {
    private val reservations = firestore.collection("reservation")

    /**
     * Converts a Firestore reservation document into a
     * ReservedUser using consistent field and date validation
     * for both entrant and owner reservation features.
     *
     * @param document Firestore reservation document.
     * @return Valid reservation or null if required fields
     * are missing or invalid.
     */
    private fun parseReservation(
        document: DocumentSnapshot
    ): ReservedUser? {

        val accountId = document.get("accountId") as? String
            ?: return null

        val campsiteId = document.get("campsiteId") as? String
            ?: return null

        if (accountId.isBlank() || campsiteId.isBlank()) {
            return null
        }

        val start = document.get("bookedPeriodStart") as? Timestamp
            ?: return null

        val end = document.get("bookedPeriodEnd") as? Timestamp
            ?: return null

        if (!start.toDate().before(end.toDate())) {
            return null
        }

        val cancelled = document.get("cancelled") as? Boolean
            ?: return null

        return ReservedUser(
            id = document.id,
            accountId = accountId,
            campsiteId = campsiteId,
            bookedPeriodStart = start.toDate(),
            bookedPeriodEnd = end.toDate(),
            cancelled = cancelled
        )
    }

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
                        parseReservation(document)


                        // Skip reservations with missing or invalid dates.
                        val start = document.get("bookedPeriodStart") as? Timestamp
                            ?: return@mapNotNull null

                        val end = document.get("bookedPeriodEnd") as? Timestamp
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

                        val cancelled = document.get("cancelled") as? Boolean
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

    /**
     * Listens for real-time reservation changes for a campsite.
     *
     * @param campsiteId ID of the campsite.
     * @param onSuccess Called when reservation records change.
     * @param onError Called when retrieval fails.
     * @return Listener registration used to stop listening.
     */
    fun listenToCampsiteReservations(
        campsiteId: String,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        return reservations
            .whereEqualTo("campsiteId", campsiteId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onError(
                        IllegalStateException(
                            "Unable to load reservations."
                        )
                    )
                    return@addSnapshotListener
                }

                try {
                    val results = snapshot.documents.mapNotNull { document ->
                        parseReservation(document)

                        val start = document.get("bookedPeriodStart") as? Timestamp
                            ?: return@mapNotNull null

                        val end = document.get("bookedPeriodEnd") as? Timestamp
                            ?: return@mapNotNull null

                        if (!start.toDate().before(end.toDate())) {
                            return@mapNotNull null
                        }

                        val accountId = document.getString("accountId")
                            ?: return@mapNotNull null

                        if (accountId.isBlank()) {
                            return@mapNotNull null
                        }

                        val cancelled = document.get("cancelled") as? Boolean
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
    }
}