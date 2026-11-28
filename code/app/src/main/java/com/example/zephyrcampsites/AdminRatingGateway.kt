// Exposes campsite feedback and moderation actions to the admin screen.
package com.example.zephyrcampsites

// Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
interface AdminRatingGateway {

    fun observeAdminRatings(
        campsiteId: String,
        onRatings: (List<CampsiteRating>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit

    fun removeAdminRating(
        campsiteId: String,
        adminAccountId: String,
        selected: CampsiteRating,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    )

    fun removeAdminReview(
        campsiteId: String,
        adminAccountId: String,
        selected: CampsiteRating,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    )
}
