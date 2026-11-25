package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Timestamp

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

        loadReservations(reservations.whereEqualTo("accountId", accountId), onSuccess, onError)
    }

    /**
     * Loads confirmed reservations for the owner's selected campsite.
     * @param owner Current campsite owner.
     * @param campsiteId Selected campsite ID.
     * @param onSuccess Receives the campsite's reservations in start date order.
     * @param onError Called when ownership cannot be verified or retrieval fails.
     */
    internal fun getCampsiteReservations(
        owner: Account,
        campsiteId: String,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val ownershipError = IllegalArgumentException("Only the campsite owner can open these reservations.")
        if (OWNER !in owner.userType || owner.id.isBlank() || campsiteId.isBlank() ||
            campsiteId.contains("/") || campsiteId == "." || campsiteId == ".."
        ) {
            onError(ownershipError)
            return
        }

        firestore.collection("campsite").document(campsiteId).get(Source.SERVER)
            .addOnSuccessListener { campsite ->
                if (!campsite.exists() || campsite.get("ownerID") != owner.id) {
                    onError(ownershipError)
                } else {
                    loadReservations(
                        reservations.whereEqualTo("campsiteId", campsiteId),
                        onSuccess = { onSuccess(it.filterNot { reservation -> reservation.cancelled }
                            .sortedBy { reservation -> reservation.bookedPeriodStart }) },
                        onError = onError
                    )
                }
            }
            .addOnFailureListener(onError)
    }

    private fun loadReservations(
        query: Query,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        query.get(Source.SERVER).addOnSuccessListener { snapshot ->
            try {
                val results = snapshot.documents.mapNotNull { document ->
                    val start = (document.get("bookedPeriodStart") as? Timestamp)?.toDate()
                        ?: return@mapNotNull null
                    val end = (document.get("bookedPeriodEnd") as? Timestamp)?.toDate()
                        ?: return@mapNotNull null
                    val campsiteId = document.get("campsiteId") as? String ?: return@mapNotNull null
                    val accountId = document.get("accountId") as? String ?: return@mapNotNull null
                    val cancelled = document.get("cancelled") as? Boolean ?: return@mapNotNull null
                    if (!start.before(end) || campsiteId.isBlank() || accountId.isBlank()) {
                        return@mapNotNull null
                    }
                    ReservedUser(document.id, accountId, campsiteId, start, end, cancelled)
                }
                onSuccess(results)
            } catch (exception: Exception) {
                onError(exception)
            }
        }.addOnFailureListener(onError)
    }
}