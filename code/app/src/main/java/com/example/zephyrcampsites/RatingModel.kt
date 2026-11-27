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
) : RatingGateway, AdminRatingGateway {
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
     * Watches the same rating records that the PR #141 confirmation screen displays.
     * @param campsiteId Campsite being moderated.
     * @param onRatings Receives the feedback and its saved version.
     * @param onError Receives a load error.
     * @return Removes the snapshot listener when the screen closes.
     */
    override fun observeAdminRatings(
        campsiteId: String, onRatings: (List<CampsiteRating>) -> Unit, onError: (String) -> Unit
    ): () -> Unit {
        val listener = campsites.document(campsiteId).collection("ratings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError("Unable to load campsite feedback.")
            } else {
                onRatings(snapshot?.documents.orEmpty().map { document ->
                    CampsiteRating(
                        reservationId = document.getString("reservationId").orEmpty(),
                        score = document.getLong("score")?.toInt() ?: 0,
                        review = document.getString("review").orEmpty(),
                        accountId = document.id,
                        updatedAt = document.getTimestamp("updatedAt")
                    )
                })
            }
        }
        return { listener.remove() }
    }

    // AI Citation [AI-39] (Bhav Saluja). Connects PR #141 to version-checked server moderation.
    /**
     * Removes only the version displayed when the administrator confirmed the selection.
     * @param campsiteId Campsite containing the feedback.
     * @param adminAccountId Profile ID of the current administrator.
     * @param selected Feedback snapshot displayed for confirmation.
     * @param onResult Called after the server removes the selected version.
     * @param onError Receives a stale-selection or other removal error.
     */
    override fun removeAdminRating(
        campsiteId: String, adminAccountId: String, selected: CampsiteRating,
        onResult: (Boolean) -> Unit, onError: (String) -> Unit
    ) {
        val version = selected.updatedAt
        val accountId = selected.accountId
        if (version == null || accountId == null) {
            onError("Reload the rating and try again.")
            return
        }
        functions.getHttpsCallable("removeCampsiteFeedback")
            .call(mapOf(
                "campsiteId" to campsiteId, "accountId" to accountId, "adminAccountId" to adminAccountId,
                "updatedAt" to mapOf("seconds" to version.seconds, "nanoseconds" to version.nanoseconds)
            ))
            .addOnSuccessListener { onResult(true) }
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
