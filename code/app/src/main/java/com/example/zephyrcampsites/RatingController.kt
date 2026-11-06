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
        if (account?.userType != "entrant" || !validId(account.id) || !validId(campsiteId)) {
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
        if (account?.userType != "entrant" || !validId(account.id) || !validId(campsiteId)) {
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

    /** Checks that an ID identifies one Firestore document. */
    private fun validId(id: String): Boolean = id.isNotBlank() && !id.contains("/")
}
