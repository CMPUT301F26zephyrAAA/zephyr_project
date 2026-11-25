package com.example.zephyrcampsites

import com.google.firebase.firestore.ListenerRegistration

/** Defines the rating operations shared by the Firestore model and local test models. */
interface RatingGateway {
    /**
     * Reads a saved rating after finding a completed stay.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant account ID.
     * @param onResult Receives the rating, or null if no eligible stay exists.
     * @param onError Receives a readable load error.
     */
    fun loadRating(campsiteId: String, accountId: String, onResult: (CampsiteRating?) -> Unit, onError: (String) -> Unit)

    /**
     * Rechecks the stay and saves the entrant's rating and campsite average together.
     *
     * @param campsiteId Campsite being rated.
     * @param accountId Entrant account ID.
     * @param rating Rating and qualifying reservation ID.
     * @param onSuccess Called after the write succeeds.
     * @param onError Receives a readable save error.
     */
    fun submitRating(
        campsiteId: String, accountId: String, rating: CampsiteRating,
        onSuccess: () -> Unit, onError: (String) -> Unit
    )

    /** Observes the written reviews for this campsite. */
    fun observeReviews(
        campsiteId: String, onResult: (List<String>) -> Unit, onError: (String) -> Unit
    ): ListenerRegistration

    /** Saves required review text for a completed reservation without changing the star score. */
    fun submitReview(
        campsiteId: String, accountId: String, reservationId: String, review: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    )
}
