// Loads reservations and submits authenticated owner cancellations.
package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.functions.FirebaseFunctions

internal data class OwnerReservation(
    val id: String,
    val campsiteName: String,
    val entrantName: String,
    val startMillis: Long,
    val endMillis: Long,
    val cancelled: Boolean
)

internal data class OwnerReservationList(val serverMillis: Long, val reservations: List<OwnerReservation>)

/**
 * Model in charge of retrieving reservation records from Firestore
 */
class ReservationsModel(private val firestore: FirebaseFirestore = Firebase.firestore) {
    private val reservations = firestore.collection("reservation")

    // AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, owner reservations and server-time cancellation.
    /** Loads the current owner's bookings and the server time used by the owner screen. */
    internal fun getOwnerReservations(onSuccess: (OwnerReservationList) -> Unit, onError: (Exception) -> Unit) {
        FirebaseFunctions.getInstance().getHttpsCallable("listOwnerReservations").call()
            .addOnSuccessListener { response ->
                try {
                    val data = response.data as Map<*, *>
                    val rows = (data["reservations"] as List<*>).map { value ->
                        val row = value as Map<*, *>
                        OwnerReservation(
                            row["id"] as String,
                            row["campsiteName"] as? String ?: "",
                            row["entrantName"] as? String ?: "",
                            (row["startMillis"] as Number).toLong(),
                            (row["endMillis"] as Number).toLong(),
                            row["cancelled"] as Boolean
                        )
                    }
                    onSuccess(OwnerReservationList((data["serverMillis"] as Number).toLong(), rows))
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
            .addOnFailureListener(onError)
    }

    /** Cancels a booking when Firestore verifies the owner and server-side check-in cutoff. */
    internal fun cancelOwnerReservation(
        reservationId: String, ownerId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit
    ) {
        reservations.document(reservationId).update(mapOf(
            "cancelled" to true,
            "status" to "cancelled",
            "cancelledBy" to ownerId,
            "cancelledAt" to FieldValue.serverTimestamp()
        ))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
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
}
