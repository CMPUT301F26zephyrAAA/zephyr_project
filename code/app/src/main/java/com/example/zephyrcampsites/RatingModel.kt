package com.example.zephyrcampsites

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore

/**
 * Reads completed stays from reservation and stores one rating per account in campsite/{id}/ratings.
 * Saves the rating values and average together so campsite snapshot listeners refresh the list.
 * Reservation creation must provide accountId, campsiteId, bookedPeriodStart, bookedPeriodEnd, and cancelled.
 *
 * @param firestore Database used for reservations, accounts, and campsite ratings.
 */
class RatingModel(private val firestore: FirebaseFirestore = Firebase.firestore) : RatingGateway, AdminRatingGateway {
    private val reservations = firestore.collection("reservation")
    private val campsites = firestore.collection("campsite")

    /**
     * Finds a completed stay and loads this account's current score and review.
     * Loads value only once.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant whose completed reservations are checked.
     * @param onResult Receives the saved rating, or null if no completed stay qualifies.
     * @param onError Receives a readable load error.
     */
    override fun loadRating(
        campsiteId: String,
        accountId: String,
        onResult: (CampsiteRating?) -> Unit,
        onError: (String) -> Unit,
    ) {
        findEligibleStay(
            campsiteId,
            accountId,
            onResult = { stay ->
                if (stay == null) {
                    onResult(null)
                } else {
                    campsites.document(campsiteId).collection("ratings")
                        .document(accountId)
                        .get(Source.SERVER)
                        .addOnSuccessListener { document ->
                            onResult(document.toCampsiteRating(stay.id))
                        }
                        .addOnFailureListener {
                            onError("Unable to load your rating. Please try again.")
                        }
                }
            },
            onError = onError,
        )
    }

    // Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
    /**
     * Finds a completed stay and loads this account's current score and review.
     * Has a listener that reloads every time the rating is updated.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant whose completed reservations are checked.
     * @param onResult Receives the saved rating, or null if no completed stay qualifies.
     * @param onError Receives a readable load error.
     */
    override fun observeRating(
        campsiteId: String,
        accountId: String,
        onResult: (CampsiteRating?) -> Unit,
        onError: (String) -> Unit,
    ): () -> Unit {
        var active = true
        var registration: ListenerRegistration? = null

        findEligibleStay(
            campsiteId,
            accountId,
            onResult = { stay ->
                if (active) {
                    if (stay == null) {
                        onResult(null)
                    } else {
                        registration = campsites.document(campsiteId).collection("ratings")
                            .document(accountId)
                            .addSnapshotListener(MetadataChanges.INCLUDE) { document, error ->
                                if (active) {
                                    when {
                                        error != null ->
                                            onError("Unable to load your rating. Please try again.")

                                        document != null && !document.metadata.isFromCache ->
                                            onResult(document.toCampsiteRating(stay.id))
                                    }
                                }
                            }
                    }
                }
            },
            onError = { message -> if (active) onError(message) },
        )

        return {
            active = false
            registration?.remove()
        }
    }

    // Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
    /**
     * Finds a completed stay and loads this account's current score and review.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant whose completed reservations are checked.
     * @param onResult Receives the saved rating, or null if no completed stay qualifies.
     * @param onError Receives a readable load error.
     */
    private fun findEligibleStay(
        campsiteId: String,
        accountId: String,
        onResult: (ReservedUser?) -> Unit,
        onError: (String) -> Unit,
    ) {
        reservations.whereEqualTo("accountId", accountId).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val stay = snapshot.documents.mapNotNull(::readReservation)
                    .filter { RatingValidator.hasStayed(it, accountId, campsiteId, now) }
                    .maxByOrNull { it.bookedPeriodEnd?.time ?: Long.MIN_VALUE }
                onResult(stay)
            }
            .addOnFailureListener {
                onError("Unable to check your past stays. Please try again.")
            }
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
            val roles = accountDocument.get("userType") as? List<*>
            check(roles?.contains("entrant") == true) { "Only entrants can rate a campsite." }
            check(reservation != null && RatingValidator.hasStayed(
                reservation, accountId, campsiteId, System.currentTimeMillis()
            )) { "You can only rate a campsite after a non-cancelled stay has ended." }
            checkCompletedStay(campsiteDocument, transaction.get(reservationRef), transaction.get(accountRef))

            val scores = (campsiteDocument.get("rating") as? List<*>)?.map { value ->
                val number = value as? Number ?: error("Invalid stored rating.")
                check(number.toDouble() == number.toInt().toDouble()) { "Invalid stored rating." }
                number.toInt()
            } ?: emptyList()
            val previousScore = if (previousRating.contains("score")) {
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

    // Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
    /**
     * Loads all ratings for a campsite. (Admin only feature)
     *
     * @param campsiteId Campsite being rated.
     * @param onRatings Receives the list of ratings.
     * @param onError Receives a readable load error.
     */
    override fun observeAdminRatings(
        campsiteId: String,
        onRatings: (List<CampsiteRating>) -> Unit,
        onError: (String) -> Unit,
    ): () -> Unit {
        val registration = campsites.document(campsiteId).collection("ratings")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    error != null ->
                        onError("Unable to load campsite ratings.")

                    snapshot != null && !snapshot.metadata.isFromCache ->
                        onRatings(snapshot.documents.map { it.toCampsiteRating() })
                }
            }

        return { registration.remove() }
    }

    /**
     * Removes an individual's rating from a campsite.
     *
     * @param campsiteId Campsite being rated.
     * @param selected Rating to remove.
     * @param onResult Called after the rating is removed.
     * @param onError Receives a readable error.
     */
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    // Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
    /**
     * Removes an individual's rating from a campsite. (Admin only feature)
     *
     * @param campsiteId Campsite being rated.
     * @param selected Rating to remove.
     * @param onResult Called after the rating is removed.
     * @param onError Receives a readable error.
     */
    override fun removeAdminRating(
        campsiteId: String,
        adminAccountId: String,
        selected: CampsiteRating,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (adminAccountId.isBlank() || '/' in adminAccountId) {
            onError("Administrator access required.")
            return
        }
        if (campsiteId.isBlank() || '/' in campsiteId) {
            onError("Invalid campsite ID.")
            return
        }
        val accountId = selected.accountId
        if (accountId.isNullOrBlank() || '/' in accountId) {
            onError("This rating has no valid document ID.")
            return
        }

        val adminRef = firestore.collection("accounts").document(adminAccountId)
        val campsiteRef = campsites.document(campsiteId)
        val ratingRef = campsiteRef.collection("ratings").document(accountId)

        firestore.runTransaction { transaction ->
            val adminDocument = transaction.get(adminRef)
            check(adminDocument.exists() && "admin" in accountRoles(adminDocument.get("userType"))) {
                "Administrator access required."
            }
            val ratingDocument = transaction.get(ratingRef)
            if (!ratingDocument.exists()) return@runTransaction false

            check(ratingDocument.toCampsiteRating() == selected) {
                "The rating changed after it was selected."
            }

            val campsiteDocument = transaction.get(campsiteRef)
            check(campsiteDocument.exists()) { "Campsite no longer available." }

            val scores = RatingValidator.removeRating(readCampsiteScores(campsiteDocument), selected.score)
            val average: Any = RatingValidator.averageAfterRemoval(scores) ?: FieldValue.delete()

            transaction.update(
                campsiteRef,
                mapOf("rating" to scores, "averageRating" to average),
            )
            transaction.delete(ratingRef)
            true
        }
            .addOnSuccessListener { removed -> onResult(removed) }
            .addOnFailureListener {
                onError("Unable to remove this rating. Verify you administrator privileges and try again.")
            }
    }

    // Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
    /**
     * Converts a Firestore document to a rating.
     *
     * @param document Rating snapshot to read.
     * @param eligibleReservationId Reservation used to verify the completed stay.
     */
    private fun DocumentSnapshot.toCampsiteRating(
        eligibleReservationId: String? = null,
    ): CampsiteRating {
        val number = get("score") as? Number
        val score = number?.let { value ->
            value.toInt().takeIf { it in 1..5 && value.toDouble() == it.toDouble() }
        } ?: 0

        return CampsiteRating(
            reservationId = eligibleReservationId ?: (get("reservationId") as? String).orEmpty(),
            score = score,
            review = (get("review") as? String).orEmpty(),
            accountId = if (exists()) id else null,
            updatedAt = get("updatedAt") as? Timestamp,
        )
    }

    // Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
    /**
     * Converts a Firestore document to a list of scores.
     *
     * @param document Campsite snapshot to read.x
     */
    private fun readCampsiteScores(document: DocumentSnapshot): List<Int> =
        (document.get("rating") as? List<*>)?.map { value ->
            val number = value as? Number ?: error("Invalid stored rating.")
            check(number.toDouble() == number.toInt().toDouble()) { "Invalid stored rating." }
            number.toInt()
        } ?: emptyList()

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
        val text = review.trim()
        if (text.isEmpty()) {
            onError("Enter a review before submitting.")
            return
        }

        val campsiteRef = campsites.document(campsiteId)
        val ratingRef = campsiteRef.collection("ratings").document(accountId)
        val reservationRef = reservations.document(reservationId)
        val accountRef = firestore.collection("accounts").document(accountId)

        firestore.runTransaction { transaction ->
            checkCompletedStay(transaction.get(campsiteRef), transaction.get(reservationRef), transaction.get(accountRef))
            transaction.set(ratingRef, mapOf(
                "accountId" to accountId, "campsiteId" to campsiteId, "reservationId" to reservationId,
                "review" to text, "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge())
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError("Unable to save your review. Check your completed stay and try again.") }
    }

    /** Verifies the campsite, entrant, and completed reservation from the transaction snapshots. */
    private fun checkCompletedStay(campsite: DocumentSnapshot, stay: DocumentSnapshot, account: DocumentSnapshot) {
        check(campsite.exists()) { "Campsite no longer available." }
        val role = account.get("userType")
        check(role == "entrant" || (role is List<*> && "entrant" in role)) { "Only entrants can rate or review a campsite." }
        check(account.getBoolean("deletionPending") != true) { "Your profile is being deleted." }
        val reservation = readReservation(stay)
        check(reservation != null && RatingValidator.hasStayed(
            reservation, account.id, campsite.id, System.currentTimeMillis()
        )) { "A non-cancelled stay must have ended." }
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
