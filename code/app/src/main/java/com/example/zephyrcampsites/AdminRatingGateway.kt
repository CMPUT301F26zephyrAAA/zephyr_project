package com.example.zephyrcampsites

interface AdminRatingGateway {

    fun observeAdminRatings(
        campsiteId: String,
        onRatings: (List<CampsiteRating>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit

    fun removeAdminRating(
        campsiteId: String,
        selected: CampsiteRating,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    )
}