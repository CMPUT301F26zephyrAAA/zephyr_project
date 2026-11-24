package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore

/**
 * Reads completed stays from reservation and stores one rating per account in campsite/{id}/ratings.
 * Saves the rating values and average together so campsite snapshot listeners refresh the list.
 * Reservation creation must provide accountId, campsiteId, bookedPeriodStart, bookedPeriodEnd, and cancelled.
 *
 * @param firestore Database used for reservations, accounts, and campsite ratings.
 */
class RatingModel(private val firestore: FirebaseFirestore = Firebase.firestore) : RatingGateway {
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
        val campsiteRef = campsites.document(campsiteId)
        val ratingRef = campsiteRef.collection("ratings").document(accountId)
        val reservationRef = reservations.document(rating.reservationId)
        val accountRef = firestore.collection("accounts").document(accountId)

        firestore.runTransaction { transaction ->
            val campsiteDocument = transaction.get(campsiteRef)
            val previousRating = transaction.get(ratingRef)
            val reservation = readReservation(transaction.get(reservationRef))
            val accountDocument = transaction.get(accountRef)
            check(campsiteDocument.exists()) { "Campsite no longer available." }
            check(accountDocument.getString("userType") == "entrant") { "Only entrants can rate a campsite." }
            check(reservation != null && RatingValidator.hasStayed(
                reservation, accountId, campsiteId, System.currentTimeMillis()
            )) { "You can only rate a campsite after a non-cancelled stay has ended." }

            val scores = (campsiteDocument.get("rating") as? List<*>)?.map { value ->
                val number = value as? Number ?: error("Invalid stored rating.")
                check(number.toDouble() == number.toInt().toDouble()) { "Invalid stored rating." }
                number.toInt()
            } ?: emptyList()
            val previousScore = if (previousRating.exists()) {
                previousRating.getLong("score")?.toInt() ?: error("Invalid previous rating.")
            } else {
                null
            }
            val updated = RatingValidator.replaceRating(scores, previousScore, rating.score)
            transaction.update(campsiteRef, mapOf("rating" to updated, "averageRating" to updated.average()))
            transaction.set(ratingRef, mapOf(
                "accountId" to accountId, "campsiteId" to campsiteId, "reservationId" to rating.reservationId,
                "score" to rating.score, "review" to rating.review, "updatedAt" to FieldValue.serverTimestamp()
            ))
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError("Unable to save your rating. Check your completed stay and try again.") }
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
