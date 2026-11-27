// Loads campsite feedback and submits verified changes through Cloud Functions.
package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions

/**
 * Reads completed stays and each entrant's feedback in the campsite's ratings subcollection.
 * Updates feedback and the campsite aggregate together through Cloud Functions.
 * Reservation creation must provide accountId, campsiteId, bookedPeriodStart, bookedPeriodEnd, and cancelled.
 *
 * @param firestore Database used for reservations and campsite ratings.
 * @param functions Callable endpoints for verified feedback changes.
 */
class RatingModel(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : RatingGateway {
    private val reservations = firestore.collection("reservation")
    private val campsites = firestore.collection("campsite")

    /**
     * Finds a completed stay and loads this account's current score and review.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant whose completed reservations are checked.
     * @param onResult Receives the saved rating, or null if no completed stay qualifies.
     * @param onError Receives a readable load error.
     */
    override fun loadRating(
        campsiteId: String, accountId: String,
        onResult: (CampsiteRating?) -> Unit, onError: (String) -> Unit
    ) {
        reservations.whereEqualTo("accountId", accountId).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val stay = snapshot.documents.mapNotNull { readReservation(it) }
                    .filter { RatingValidator.hasStayed(it, accountId, campsiteId, System.currentTimeMillis()) }
                    .maxByOrNull { it.bookedPeriodEnd?.time ?: Long.MIN_VALUE }
                if (stay == null) {
                    onResult(null)
                } else {
                    campsites.document(campsiteId).collection("ratings").document(accountId).get(Source.SERVER)
                        .addOnSuccessListener { document ->
                            onResult(CampsiteRating(stay.id, document.getLong("score")?.toInt() ?: 0,
                                document.getString("review") ?: ""))
                        }
                        .addOnFailureListener { onError("Unable to load your rating. Please try again.") }
                }
            }
            .addOnFailureListener { onError("Unable to check your past stays. Please try again.") }
    }

    /**
     * Rechecks eligibility and atomically replaces the entrant's rating and campsite average.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant whose rating is saved.
     * @param rating Selected score, optional review, and qualifying reservation ID.
     * @param onSuccess Called after Firestore commits both rating updates.
     * @param onError Receives a readable save error.
     */
    override fun submitRating(
        campsiteId: String, accountId: String, rating: CampsiteRating,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        saveFeedback(campsiteId, accountId, rating.reservationId, rating.review, rating.score, onSuccess, onError)
    }

    override fun observeReviews(
        campsiteId: String, onResult: (List<String>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration {
        return campsites.document(campsiteId).collection("ratings")
            .whereGreaterThan("review", "")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("Unable to load campsite reviews.")
                } else if (snapshot != null) {
                    val reviews = snapshot.documents.sortedByDescending { it.get("updatedAt") as? Timestamp }
                        .mapNotNull { (it.get("review") as? String)?.takeIf { text -> text.isNotBlank() } }
                    onResult(reviews)
                }
            }
    }

    override fun submitReview(
        campsiteId: String, accountId: String, reservationId: String, review: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        saveFeedback(campsiteId, accountId, reservationId, review, null, onSuccess, onError)
    }

    // AI Citation [AI-39] (Bhav Saluja). Verified feedback transactions. See the team AI Documentation Google Doc.
    private fun saveFeedback(
        campsiteId: String, accountId: String, reservationId: String, review: String, score: Int?,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val data = mutableMapOf<String, Any>(
            "campsiteId" to campsiteId, "accountId" to accountId,
            "reservationId" to reservationId, "review" to review
        )
        if (score != null) {
            data["score"] = score
        }
        functions.getHttpsCallable("saveCampsiteFeedback").call(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Unable to save campsite feedback.") }
    }

    /**
     * Watches rating and review records for campsite moderation.
     * @param campsiteId Campsite being moderated.
     * @param onResult Receives current feedback.
     * @param onError Receives a load error.
     * @return Registration removed when the moderation screen closes.
     */
    internal fun observeFeedback(
        campsiteId: String, onResult: (List<CampsiteFeedback>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration =
        campsites.document(campsiteId).collection("ratings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError("Unable to load campsite feedback.")
            } else {
                onResult(snapshot?.documents.orEmpty().map { document ->
                    CampsiteFeedback(document.id, document.getLong("score"), document.getString("review").orEmpty())
                })
            }
        }

    /**
     * Removes feedback as a verified administrator and updates the campsite aggregate.
     * @param campsiteId Campsite containing the feedback.
     * @param accountId Author of the feedback.
     * @param onError Receives a removal error.
     */
    internal fun removeFeedback(campsiteId: String, accountId: String, onError: (String) -> Unit) {
        functions.getHttpsCallable("removeCampsiteFeedback")
            .call(mapOf("campsiteId" to campsiteId, "accountId" to accountId))
            .addOnFailureListener { onError(it.message ?: "Unable to remove campsite feedback.") }
    }

    /**
     * Reads the reservation contract, rejecting incomplete records rather than assuming a stay occurred.
     *
     * @param document Reservation snapshot to read.
     * @return Complete reservation, or null if any required field is missing.
     */
    private fun readReservation(document: DocumentSnapshot): ReservedUser? {
        val data = document.data ?: return null
        return ReservedUser(
            id = document.id,
            accountId = data["accountId"] as? String ?: return null,
            campsiteId = data["campsiteId"] as? String ?: return null,
            bookedPeriodStart = (data["bookedPeriodStart"] as? Timestamp)?.toDate() ?: return null,
            bookedPeriodEnd = (data["bookedPeriodEnd"] as? Timestamp)?.toDate() ?: return null,
            cancelled = data["cancelled"] as? Boolean ?: return null
        )
    }
}
