package com.example.zephyrcampsites

/**
 * Validates entrant rating requests before delegating storage to the model.
 *
 * @param model Model used to read eligibility and save ratings.
 */
class RatingController(private val model: RatingGateway = RatingModel()) {
    /**
     * Loads rating eligibility for an entrant.
     *
     * @param campsiteId Campsite being rated.
     * @param account Current account, or null if identification has not finished.
     * @param onResult Receives the rating, or null when rating is not available.
     * @param onError Receives a readable load error.
     */
    fun loadRating(
        campsiteId: String, account: Account?,
        onResult: (CampsiteRating?) -> Unit, onError: (String) -> Unit
    ) {
        if (account == null || "entrant" !in account.userType || !validId(campsiteId) || !validId(account.id)) {
            onResult(null)
            return
        }
        model.loadRating(campsiteId, account.id, onResult, onError)
    }

    /**
     * Saves a score from 1 to 5 for the entrant's completed stay.
     *
     * @param campsiteId Campsite being rated.
     * @param account Current entrant account.
     * @param rating Selected score, optional review, and qualifying reservation.
     * @param onSuccess Called after the rating is saved.
     * @param onError Receives a readable validation or save error.
     */
    fun submitRating(
        campsiteId: String, account: Account?, rating: CampsiteRating,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        if (account == null || "entrant" !in account.userType || !validId(campsiteId) || !validId(account.id)) {
            onError("Only an identified entrant can rate a campsite.")
            return
        }
        if (!validId(rating.reservationId)) {
            onError("A completed reservation is required.")
            return
        }
        if (rating.score !in 1..5) {
            onError("Select a rating from 1 to 5.")
            return
        }
        model.submitRating(campsiteId, account.id, rating.copy(review = rating.review.trim()), onSuccess, onError)
    }

    /** Receives the campsite's current reviews and subsequent Firestore updates. */
    internal fun observeReviews(campsiteId: String, onResult: (List<String>) -> Unit, onError: (String) -> Unit) =
        model.observeReviews(campsiteId, onResult, onError)

    /**
     * Saves a written review for an identified entrant.
     *
     * @param campsiteId Campsite being reviewed.
     * @param account Entrant submitting the review.
     * @param reservationId Completed stay to verify.
     * @param review Required review text.
     * @param onSuccess Called when the review is saved.
     * @param onError Receives a validation or save error.
     */
    internal fun submitReview(
        campsiteId: String, account: Account, reservationId: String, review: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        if ("entrant" !in account.userType || !validId(campsiteId) || !validId(account.id) || !validId(reservationId)) {
            onError("A completed reservation is required to write a review.")
            return
        }
        model.submitReview(campsiteId, account.id, reservationId, review, onSuccess, onError)
    }

    /** Checks that an ID identifies one Firestore document. */
    private fun validId(id: String): Boolean = id.isNotBlank() && !id.contains("/")
}
