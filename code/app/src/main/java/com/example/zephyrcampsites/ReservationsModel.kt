// Loads reservations and submits authenticated owner cancellations.
package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.functions.FirebaseFunctions

internal data class OwnerReservation(
    val id: String,
    val campsiteName: String,
    val entrantName: String,
    val startMillis: Long,
    val endMillis: Long,
    val cancelled: Boolean,
    val entrantAccountId: String = ""
)

internal data class OwnerReservationList(val serverMillis: Long, val reservations: List<OwnerReservation>)

/**
 * Model in charge of retrieving reservation records from Firestore
 */
class ReservationsModel(private val firestore: FirebaseFirestore = Firebase.firestore) {
    private val reservations = firestore.collection("reservation")

    // AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, owner reservations and server-time cancellation.
    // AI Citation [AI-43] (Bhav Saluja)
    // OpenAI, Codex, November 27, 2026. Added owner-scoped reservation loading for chat.
    // See the team AI Documentation Google Doc.
    /**
     * Loads owned bookings and the server time used by the owner screen.
     * @param onSuccess Receives the bookings and current server time.
     * @param onError Called when the server cannot load the bookings.
     * @param campsiteId Limits the request to one campsite for reservation chat when supplied.
     */
    internal fun getOwnerReservations(
        onSuccess: (OwnerReservationList) -> Unit,
        onError: (Exception) -> Unit,
        campsiteId: String? = null
    ) {
        val request = campsiteId?.let { mapOf("campsiteId" to it) }
        FirebaseFunctions.getInstance().getHttpsCallable("listOwnerReservations").call(request)
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
                            row["cancelled"] as Boolean,
                            row["entrantAccountId"] as? String ?: ""
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
     * @param accountId Current entrant's Firebase Auth profile ID.
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
